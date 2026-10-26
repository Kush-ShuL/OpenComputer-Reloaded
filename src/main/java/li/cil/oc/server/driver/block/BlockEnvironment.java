package li.cil.oc.server.driver.block;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

abstract class BlockEnvironment extends AbstractManagedEnvironment {
    protected final Level level;
    protected final BlockPos position;
    protected final Direction side;
    BlockEnvironment(Level level, BlockPos position, Direction side, String name) {
        this.level = level; this.position = position.immutable(); this.side = side;
        setNode(Network.newNode(this, Visibility.Network).withComponent(name).create());
    }
    protected boolean permitted() {
        if (!(level instanceof ServerLevel server) || !level.hasChunkAt(position)) return false;
        var player = FakePlayerFactory.get(server, new GameProfile(UUID.fromString(Settings.get().fakePlayerUuid), Settings.get().fakePlayerName));
        synchronized (player) {
            player.setPos(position.getX() + .5, position.getY() + .5, position.getZ() + .5);
            if (level.getBlockEntity(position) instanceof Container container && !container.stillValid(player)) return false;
            var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, position, new BlockHitResult(Vec3.atCenterOf(position), side == null ? Direction.DOWN : side, position, false));
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled() && event.getUseBlock() != TriState.FALSE;
        }
    }
}
