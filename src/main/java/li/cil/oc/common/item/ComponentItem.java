package li.cil.oc.common.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Tiered;
import li.cil.oc.util.PackedColor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Hardware items whose functionality is supplied by their real component drivers. */
public final class ComponentItem extends Item implements Tiered {
    public enum Kind { PROCESSOR, APU, MEMORY, EEPROM, FLOPPY, HDD, GRAPHICS, NETWORK, WIRELESS }
    private final Kind kind;
    private final int level;
    private static volatile BiConsumer<Player, InteractionHand> driveUi;

    public ComponentItem(Properties properties, Kind kind, int level) {
        super(properties.rarity(switch (kind == Kind.APU ? level == 2 ? 3 : Math.min(2, level + 1) : kind == Kind.MEMORY ? level / 2 : level) {
            case 0 -> Rarity.COMMON;
            case 1 -> Rarity.UNCOMMON;
            case 2 -> Rarity.RARE;
            default -> Rarity.EPIC;
        }));
        this.kind = Objects.requireNonNull(kind); this.level = level;
    }
    public Kind kind() { return kind; }
    public int level() { return level; }
    @Override public int tier() { return kind == Kind.APU ? Math.min(2, level + 1) : kind == Kind.MEMORY ? level / 2 : level; }
    public boolean processor() { return kind == Kind.PROCESSOR || kind == Kind.APU; }
    public boolean graphics() { return kind == Kind.GRAPHICS || kind == Kind.APU; }
    public boolean storage() { return kind == Kind.HDD || kind == Kind.FLOPPY; }
    public static void setDriveUiOpener(BiConsumer<Player, InteractionHand> opener) { driveUi = Objects.requireNonNull(opener); }

    @Override public int getMaxStackSize(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("oc:data");
        return data.getCompound("node").contains("address") ? 1 : super.getMaxStackSize(stack);
    }

    @Override public boolean doesSneakBypassUse(ItemStack stack, net.minecraft.world.level.LevelReader world, net.minecraft.core.BlockPos pos, Player player) {
        return kind == Kind.FLOPPY || kind == Kind.EEPROM;
    }

    @Override public Component getName(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("oc:data");
        if (kind == Kind.EEPROM && data.contains("oc:label")) return Component.literal(data.getString("oc:label"));
        if (kind == Kind.HDD) {
            int kb = Settings.get().hddSizes[level];
            return Component.empty().append(super.getName(stack)).append(kb >= 1024 ? " (" + kb / 1024 + "MB)" : " (" + kb + "KB)");
        }
        return super.getName(stack);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag data = root.getCompound("oc:data");
        var driver = li.cil.oc.api.Driver.driverFor(stack);
        if (kind != Kind.EEPROM && kind != Kind.FLOPPY)
            detail(tooltip, "hardware_tier", tier() + 1);
        if (processor() && driver instanceof MutableProcessor processor) {
            detail(tooltip, "cpu_components", processor.supportedComponents(stack));
            var architecture = processor.architecture(stack);
            if (architecture != null) tooltip.add(Component.translatable("oc:tooltip.cpu.Architecture", li.cil.oc.api.Machine.getArchitectureName(architecture)));
            detail(tooltip, "processor_configure");
        }
        if (kind == Kind.MEMORY) detail(tooltip, "memory_size", Settings.get().ramSizes[level]);
        if (graphics()) {
            int[] resolution = Settings.screenResolutionsByTier[level];
            detail(tooltip, "gpu_resolution", resolution[0], resolution[1]);
            detail(tooltip, "gpu_depth", PackedColor.Depth.bits(Settings.screenDepthsByTier[level]));
            detail(tooltip, "gpu_operations", switch (level) { case 0 -> "1/1/4/2/2"; case 1 -> "2/4/8/4/4"; default -> "4/8/16/8/8"; });
        }
        if (kind == Kind.EEPROM) tooltip.add(Component.translatable("oc:tooltip.eeprom").withStyle(ChatFormatting.GRAY));
        if (storage()) {
            int kiloBytes = kind == Kind.FLOPPY ? Settings.get().floppySize : Settings.get().hddSizes[level];
            detail(tooltip, "storage_size", kiloBytes);
            if (data.contains("oc:fs.label")) tooltip.add(Component.literal(data.getString("oc:fs.label")));
            if (flag.isAdvanced() && data.getCompound("fs").contains("capacity.used"))
                tooltip.add(Component.translatable("oc:tooltip.diskusage", data.getCompound("fs").getLong("capacity.used"), (long) kiloBytes * 1024));
            tooltip.add(Component.translatable(root.getBoolean("oc:unmanaged") ? "oc:tooltip.diskmodeunmanaged" : "oc:tooltip.diskmodemanaged"));
            if (!root.getString("oc:lock").isEmpty()) tooltip.add(Component.translatable("oc:tooltip.disklocked", root.getString("oc:lock")));
        }
        String address = data.getCompound("node").getString("address");
        if (!address.isEmpty()) tooltip.add(Component.literal(address.substring(0, Math.min(13, address.length())) + "...").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void detail(List<Component> tooltip, String key, Object... values) {
        tooltip.add(Component.translatable("tooltip.opencomputers." + key, values).withStyle(ChatFormatting.GRAY));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (processor() && player.isShiftKeyDown() && !level.isClientSide) {
            if (li.cil.oc.api.Driver.driverFor(stack) instanceof MutableProcessor processor) {
                var architectures = new ArrayList<>(processor.allArchitectures());
                if (!architectures.isEmpty()) {
                    int index = architectures.indexOf(processor.architecture(stack));
                    var next = architectures.get((index + 1) % architectures.size());
                    processor.setArchitecture(stack, next);
                    player.getInventory().setChanged();
                    player.displayClientMessage(Component.translatable("oc:tooltip.cpu.Architecture", li.cil.oc.api.Machine.getArchitectureName(next)), false);
                }
                player.swing(hand);
            }
        } else if (storage() && !player.isShiftKeyDown() && level.isClientSide
                && !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("oc:lootFactory")) {
            BiConsumer<Player, InteractionHand> opener = driveUi;
            if (opener == null) throw new IllegalStateException("Drive configuration screen was not initialized on the client");
            opener.accept(player, hand);
            player.swing(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void registerNetworking(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToServer(
                DriveSettingsPayload.TYPE, DriveSettingsPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                    ItemStack stack = context.player().getItemInHand(payload.hand());
                    if (!(stack.getItem() instanceof ComponentItem item) || !item.storage()) return;
                    CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    if (root.contains("oc:lootFactory") || !root.getString("oc:lock").isEmpty()) return;
                    if (root.getBoolean("oc:unmanaged") != payload.unmanaged()) {
                        CompoundTag data = root.getCompound("oc:data");
                        CompoundTag node = data.getCompound("node"); node.remove("address"); data.put("node", node);
                        root.put("oc:data", data); root.remove("oc:lock"); root.putBoolean("oc:unmanaged", payload.unmanaged());
                    }
                    if (payload.lock()) root.putString("oc:lock", context.player().getGameProfile().getName());
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
                    context.player().getInventory().setChanged();
                })));
    }

    public record DriveSettingsPayload(InteractionHand hand, boolean unmanaged, boolean lock) implements CustomPacketPayload {
        public static final Type<DriveSettingsPayload> TYPE = new Type<>(ResourceLocation.parse("opencomputers:drive_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DriveSettingsPayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeEnum(payload.hand); buffer.writeBoolean(payload.unmanaged); buffer.writeBoolean(payload.lock); },
                buffer -> new DriveSettingsPayload(buffer.readEnum(InteractionHand.class), buffer.readBoolean(), buffer.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
