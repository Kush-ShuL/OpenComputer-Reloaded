package li.cil.oc.common;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import li.cil.oc.Settings;
import li.cil.oc.common.init.MiscDevices;
import li.cil.oc.common.item.data.*;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Original crafting recovery and seasonal/new-player interactions. */
public final class MiscEvents {
    private static final String MANUAL_FLAG = "oc:receivedManual";
    private static final Set<String> RECRAFTED = Set.of("navigationupgrade", "microcontroller", "drone", "robot", "tablet");
    private MiscEvents() {}
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (!Settings.get().giveManualToNewPlayers || !(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.getPersistentData().getBoolean(MANUAL_FLAG)) return;
        player.getPersistentData().putBoolean(MANUAL_FLAG, true);
        var manual = li.cil.oc.api.Items.get("manual");
        if (manual != null) InventoryUtils.addToPlayerInventory(manual.createItemStack(1), player, true);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().getBoolean(MANUAL_FLAG)) event.getEntity().getPersistentData().putBoolean(MANUAL_FLAG, true);
    }
    private static String name(ItemStack stack) {
        var descriptor = li.cil.oc.api.Items.get(stack);
        return descriptor == null ? "" : descriptor.name();
    }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String output = name(event.getCrafting());
        if (RECRAFTED.contains(output)) {
            for (int slot = 0; slot < event.getInventory().getContainerSize(); slot++) {
                ItemStack source = event.getInventory().getItem(slot);
                if (!name(source).equals(output)) continue;
                ItemStack extra = recover(source, output, player);
                if (!extra.isEmpty()) InventoryUtils.addToPlayerInventory(extra, player, true);
            }
        } else if (!(player instanceof FakePlayer) && Settings.get().presentChance > 0 && !output.isEmpty() && player.getRandom().nextFloat() < Settings.get().presentChance && timeForPresents(LocalDate.now())) {
            player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, .2f, 1);
            InventoryUtils.addToPlayerInventory(MiscDevices.PRESENT.get().getDefaultInstance(), player, true);
        }
    }
    private static ItemStack recover(ItemStack stack, String kind, ServerPlayer player) {
        var registries = player.registryAccess();
        if (kind.equals("navigationupgrade")) {
            if (!ItemData.customData(stack).getCompound("oc:data").contains("oc:map")) return ItemStack.EMPTY;
            return new NavigationUpgradeData(stack, registries).map.copy();
        }
        ItemStack[] parts = switch (kind) {
            case "robot" -> new RobotData(stack, registries).components;
            case "tablet" -> new TabletData(stack, registries).items;
            case "drone" -> new DroneData(stack, registries).components;
            case "microcontroller" -> new MicrocontrollerData(stack, registries).components;
            default -> new ItemStack[0];
        };
        return Arrays.stream(parts).filter(part -> name(part).equals("eeprom")).findFirst().map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }
    public static boolean timeForPresents(LocalDate now) {
        int month = now.getMonthValue(), day = now.getDayOfMonth();
        return month == 12 && (day > 24 || day == 14) || month == 1 && day < 7 || month == 2 && day == 14 || month == 4 && day == 22 || month == 5 && day == 1 || month == 10 && day == 3;
    }
}
