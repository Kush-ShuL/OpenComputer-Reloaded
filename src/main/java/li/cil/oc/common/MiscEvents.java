package li.cil.oc.common;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import li.cil.oc.Settings;
import li.cil.oc.common.init.MiscDevices;
import li.cil.oc.common.item.HoverBootsItem;
import li.cil.oc.common.item.data.*;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Original wearable, crafting recovery and seasonal/new-player interactions. */
public final class MiscEvents {
    private static final ResourceLocation STEP = ResourceLocation.parse("opencomputers:hover_boot_step");
    private static final String MANUAL_FLAG = "oc:receivedManual";
    private static final Set<String> RECRAFTED = Set.of("navigationupgrade", "microcontroller", "drone", "robot", "tablet");
    private MiscEvents() {}
    private static ItemStack boots(Player player) {
        for (ItemStack stack : player.getArmorSlots()) if (stack.getItem() instanceof HoverBootsItem) return stack;
        return ItemStack.EMPTY;
    }
    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player instanceof FakePlayer) return;
        ItemStack stack = boots(player);
        boolean active = false;
        if (!stack.isEmpty()) {
            HoverBootsItem item = (HoverBootsItem) stack.getItem();
            if (!Settings.get().ignorePower && item.getCharge(stack) == 0 && !player.level().isClientSide && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
            }
            if (!player.isShiftKeyDown()) {
                if (!Settings.get().ignorePower && !player.level().isClientSide && player.onGround() && !player.getAbilities().instabuild &&
                        player.level().getGameTime() % Math.max(1, (long) Settings.get().tickFrequency) == 0 && player.getDeltaMovement().lengthSqr() > .015) {
                    item.charge(stack, -Settings.get().hoverBootMove, false);
                }
                active = Settings.get().ignorePower || item.getCharge(stack) > 0;
            }
        }
        var step = player.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) {
            if (active && !step.hasModifier(STEP)) step.addTransientModifier(new AttributeModifier(STEP, Math.max(0, 1 - step.getBaseValue()), AttributeModifier.Operation.ADD_VALUE));
            else if (!active && step.hasModifier(STEP)) step.removeModifier(STEP);
        }
        player.getPersistentData().putBoolean("oc:hasHoverBoots", active);
        if (active && !player.onGround() && player.fallDistance < 5 && player.getDeltaMovement().y < 0) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(1, .9, 1));
        }
    }
    private static boolean pay(Player player, ItemStack stack, double cost) {
        if (Settings.get().ignorePower || player.getAbilities().instabuild) return true;
        HoverBootsItem item = (HoverBootsItem) stack.getItem();
        if (item.charge(stack, -cost, true) != 0) return false;
        if (!player.level().isClientSide) item.charge(stack, -cost, false);
        return true;
    }
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player instanceof FakePlayer || player.isShiftKeyDown()) return;
        ItemStack stack = boots(player);
        if (!stack.isEmpty() && pay(player, stack, Settings.get().hoverBootJump)) {
            var motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.add(player.isSprinting() ? motion.x * .5 : 0, .4, player.isSprinting() ? motion.z * .5 : 0));
        }
    }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (event.getDistance() <= 3 || !(event.getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        ItemStack stack = boots(player);
        if (!stack.isEmpty() && pay(player, stack, Settings.get().hoverBootAbsorb)) event.setDistance(event.getDistance() * .3f);
    }
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
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, .2f, 1);
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
