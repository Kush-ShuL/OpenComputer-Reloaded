package li.cil.oc.common.integration.mekanism;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * Java adaptation of the original OpenComputers Mekanism wrench callbacks.
 * Copyright (c) 2013-2015 Florian "Sangar" Nücke; MIT, see LICENSE.
 */
public final class EventHandlerMekanism {
    // MekanismItemAbilities explicitly permits recreating its named abilities.
    private static final ItemAbility WRENCH_DISMANTLE = ItemAbility.get("wrench_dismantle");

    private EventHandlerMekanism() {}

    public static boolean isWrench(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.canPerformAction(WRENCH_DISMANTLE);
    }

    public static boolean useWrench(Player player, BlockPos position, boolean changeDurability) {
        // IMekWrench.canUseWrench is gone. Its public modern counterpart is the
        // current stack's ability: the Configurator advertises it in WRENCH mode.
        // The original hook also ignored changeDurability and did not consume charge.
        return isWrench(player.getMainHandItem());
    }
}
