package li.cil.oc.server.component;

import li.cil.oc.api.internal.Tablet;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Message;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The original eight-field tablet.use message, including the modern double hit coordinates. */
record TabletUseData(CompoundTag result, Player player, BlockPos position, Direction side,
                     float hitX, float hitY, float hitZ) {
    static TabletUseData from(Message message) {
        if (!message.name().equals("tablet.use") ||
            !(message.source().host() instanceof Machine machine) ||
            !(machine.host() instanceof Tablet)) return null;

        Object[] values = message.data();
        if (values.length != 8 || !(values[0] instanceof CompoundTag result) ||
            !(values[1] instanceof ItemStack) || !(values[2] instanceof Player player) ||
            !(values[3] instanceof BlockPos position) || !(values[4] instanceof Direction side) ||
            !isHitCoordinate(values[5]) || !isHitCoordinate(values[6]) || !isHitCoordinate(values[7])) return null;

        return new TabletUseData(result, player, position, side,
            ((Number) values[5]).floatValue(), ((Number) values[6]).floatValue(), ((Number) values[7]).floatValue());
    }

    private static boolean isHitCoordinate(Object value) {
        return value instanceof Float || value instanceof Double;
    }
}
