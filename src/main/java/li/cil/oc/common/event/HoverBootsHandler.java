package li.cil.oc.common.event;

import li.cil.oc.Settings;
import li.cil.oc.common.item.HoverBootsItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Original HoverBootsHandler from OC 2589433, using the modern step-height attribute. */
public final class HoverBootsHandler {
    private static final ResourceLocation STEP = ResourceLocation.fromNamespaceAndPath("opencomputers", "hover_boot_step");
    private static final String ACTIVE = Settings.namespace + "hasHoverBoots";
    private HoverBootsHandler() {}

    private static ItemStack equippedBoots(Player player) {
        for (ItemStack stack : player.getArmorSlots()) if (!stack.isEmpty() && stack.getItem() instanceof HoverBootsItem) return stack;
        return ItemStack.EMPTY;
    }

    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player instanceof FakePlayer) return;
        boolean previous = player.getPersistentData().getBoolean(ACTIVE);
        boolean active = false;
        if (!player.isShiftKeyDown()) {
            ItemStack stack = equippedBoots(player);
            if (!stack.isEmpty()) {
                HoverBootsItem boots = (HoverBootsItem) stack.getItem();
                if (Settings.get().ignorePower) active = true;
                else {
                    if (player.onGround() && !player.getAbilities().instabuild
                            && player.level().getGameTime() % Settings.get().tickFrequency == 0
                            && player.getDeltaMovement().lengthSqr() > .015f) {
                        boots.charge(stack, -Settings.get().hoverBootMove, false);
                    }
                    active = boots.getCharge(stack) > 0;
                }
            }
        }

        var step = player.getAttribute(Attributes.STEP_HEIGHT);
        // The old stepHeight was an absolute 1 while active and 0.5 after deactivation.
        // Recreate a missing active transient modifier after a saved player reconnects.
        if (active != previous || active && step != null && !step.hasModifier(STEP)) {
            player.getPersistentData().putBoolean(ACTIVE, active);
            if (step != null) {
                step.removeModifier(STEP);
                step.addTransientModifier(new AttributeModifier(STEP, (active ? 1 : .5) - step.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (active && !player.onGround() && player.fallDistance < 5 && player.getDeltaMovement().y < 0) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(1, .9f, 1));
        }
    }

    private static boolean pay(Player player, ItemStack stack, double cost) {
        if (Settings.get().ignorePower || player.getAbilities().instabuild) return true;
        HoverBootsItem boots = (HoverBootsItem) stack.getItem();
        if (boots.charge(stack, -cost, true) != 0) return false;
        boots.charge(stack, -cost, false);
        return true;
    }

    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player instanceof FakePlayer || player.isShiftKeyDown()) return;
        ItemStack stack = equippedBoots(player);
        if (!stack.isEmpty() && pay(player, stack, Settings.get().hoverBootJump)) {
            var motion = player.getDeltaMovement();
            player.push(player.isSprinting() ? motion.x * .5 : 0, .4, player.isSprinting() ? motion.z * .5 : 0);
        }
    }

    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (event.getDistance() <= 3 || !(event.getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        ItemStack stack = equippedBoots(player);
        if (!stack.isEmpty() && pay(player, stack, Settings.get().hoverBootAbsorb)) event.setDistance(event.getDistance() * .3f);
    }
}
