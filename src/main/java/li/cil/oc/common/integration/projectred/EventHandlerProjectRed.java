package li.cil.oc.common.integration.projectred;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Original OpenComputers ProjectRed screwdriver hooks, adapted to the public hand API.
 * Copyright (c) 2013-2015 Florian "Sangar" Nücke and contributors; MIT, see LICENSE.
 */
public final class EventHandlerProjectRed {
    private static ProjectRedAccess access;

    private EventHandlerProjectRed() {}
    static void initialize(ProjectRedAccess api) { access = api; }

    public static boolean isWrench(ItemStack stack) {
        return ProjectRedIntegration.available() && access != null && access.isScrewdriver(stack);
    }

    public static boolean useWrench(Player player, BlockPos position, boolean changeDurability) {
        return ProjectRedIntegration.available() && access != null && access.useScrewdriver(player, changeDurability);
    }
}
