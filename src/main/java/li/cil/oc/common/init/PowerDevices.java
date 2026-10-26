package li.cil.oc.common.init;

import li.cil.oc.Settings;
import li.cil.oc.common.block.CapacitorBlock;
import li.cil.oc.common.block.CarpetedCapacitorBlock;
import li.cil.oc.common.block.PowerConverterBlock;
import li.cil.oc.common.block.PowerDistributorBlock;
import li.cil.oc.common.block.entity.CapacitorBlockEntity;
import li.cil.oc.common.block.entity.CarpetedCapacitorBlockEntity;
import li.cil.oc.common.block.entity.PowerConverterBlockEntity;
import li.cil.oc.common.block.entity.PowerDistributorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.List;
import java.util.function.Supplier;

/** Independent registration of the original four working power devices. */
public final class PowerDevices {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "opencomputers");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "opencomputers");
    public static final Supplier<PowerConverterBlock> POWER_CONVERTER;
    public static final Supplier<BlockEntityType<PowerConverterBlockEntity>> POWER_CONVERTER_TYPE;
    public static final Supplier<PowerDistributorBlock> POWER_DISTRIBUTOR;
    public static final Supplier<BlockEntityType<PowerDistributorBlockEntity>> POWER_DISTRIBUTOR_TYPE;
    public static final Supplier<CapacitorBlock> CAPACITOR;
    public static final Supplier<BlockEntityType<CapacitorBlockEntity>> CAPACITOR_TYPE;
    public static final Supplier<CarpetedCapacitorBlock> CARPETED_CAPACITOR;
    public static final Supplier<BlockEntityType<CarpetedCapacitorBlockEntity>> CARPETED_CAPACITOR_TYPE;
    public static final List<Supplier<? extends Block>> ALL_BLOCKS;
    public static final List<Supplier<? extends Item>> ALL_ITEMS;
    private PowerDevices() {}
    private static BlockBehaviour.Properties metal() { return BlockBehaviour.Properties.of().strength(2, 5).sound(SoundType.METAL); }
    static {
        POWER_CONVERTER = BLOCKS.register("powerconverter", () -> new PowerConverterBlock(metal(), () -> PowerDevices.POWER_CONVERTER_TYPE.get()));
        POWER_CONVERTER_TYPE = TYPES.register("powerconverter", () -> BlockEntityType.Builder.of((pos, state) -> new PowerConverterBlockEntity(PowerDevices.POWER_CONVERTER_TYPE.get(), pos, state), POWER_CONVERTER.get()).build(null));
        POWER_DISTRIBUTOR = BLOCKS.register("powerdistributor", () -> new PowerDistributorBlock(metal(), () -> PowerDevices.POWER_DISTRIBUTOR_TYPE.get()));
        POWER_DISTRIBUTOR_TYPE = TYPES.register("powerdistributor", () -> BlockEntityType.Builder.of((pos, state) -> new PowerDistributorBlockEntity(PowerDevices.POWER_DISTRIBUTOR_TYPE.get(), pos, state), POWER_DISTRIBUTOR.get()).build(null));
        CAPACITOR = BLOCKS.register("capacitor", () -> new CapacitorBlock(metal(), () -> PowerDevices.CAPACITOR_TYPE.get()));
        CAPACITOR_TYPE = TYPES.register("capacitor", () -> BlockEntityType.Builder.of((pos, state) -> new CapacitorBlockEntity(PowerDevices.CAPACITOR_TYPE.get(), pos, state), CAPACITOR.get()).build(null));
        CARPETED_CAPACITOR = BLOCKS.register("carpetedcapacitor", () -> new CarpetedCapacitorBlock(metal(), () -> PowerDevices.CARPETED_CAPACITOR_TYPE.get()));
        CARPETED_CAPACITOR_TYPE = TYPES.register("carpetedcapacitor", () -> BlockEntityType.Builder.of((pos, state) -> new CarpetedCapacitorBlockEntity(PowerDevices.CARPETED_CAPACITOR_TYPE.get(), pos, state), CARPETED_CAPACITOR.get()).build(null));
        ALL_BLOCKS = List.of(POWER_CONVERTER, POWER_DISTRIBUTOR, CAPACITOR, CARPETED_CAPACITOR);
        ALL_ITEMS = List.of(item("powerconverter", POWER_CONVERTER), item("powerdistributor", POWER_DISTRIBUTOR), item("capacitor", CAPACITOR), item("carpetedcapacitor", CARPETED_CAPACITOR));
    }
    private static Supplier<? extends Item> item(String name, Supplier<? extends Block> block) {
        var item = ITEMS.register(name, () -> new PowerBlockItem(block.get(), name));
        Items.INSTANCE.registerBlock(name, block, item);
        return item;
    }
    public static void register(IEventBus modBus) { BLOCKS.register(modBus); ITEMS.register(modBus); TYPES.register(modBus); }

    private static final class PowerBlockItem extends BlockItem {
        private final String name;
        private PowerBlockItem(Block block, String name) { super(block, new Item.Properties()); this.name = name; }
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            tooltip.add(Component.translatable("oc:tooltip." + name));
            if (name.equals("powerconverter")) {
                tooltip.add(Component.translatable("oc:tooltip.poweracceptor", (int) Settings.get().powerConverterRate));
                tooltip.add(Component.literal("1 FE = " + Settings.get().ratioForgeEnergy + " OC"));
            }
        }
    }
}
