package li.cil.oc.common.integration.railcraft;

import mods.railcraft.api.item.Crowbar;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Original OC Railcraft wrench semantics (MIT), using Railcraft Reborn's public Crowbar API. */
public final class EventHandlerRailcraft {
    private EventHandlerRailcraft() {}

    public static boolean isWrench(ItemStack stack) {
        return RailcraftIntegration.available() && stack.getItem() instanceof Crowbar;
    }

    public static boolean useWrench(Player player, BlockPos position, boolean changeDurability) {
        if (!RailcraftIntegration.available()) return false;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof Crowbar crowbar)) return false;
        if (!changeDurability) return crowbar.canWhack(player, InteractionHand.MAIN_HAND, stack, position);
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        crowbar.onWhack(serverPlayer, InteractionHand.MAIN_HAND, stack, position);
        return true;
    }
}
