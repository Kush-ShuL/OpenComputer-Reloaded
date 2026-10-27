package li.cil.oc.server.component;

import li.cil.oc.api.internal.Drone;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.internal.Tablet;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.material.PushReaction;

/** Uses the vanilla piston resolver/mover, including adhesion and the twelve-block limit. */
public class UpgradePiston extends UpgradeBase {
    private final boolean sticky;

    public UpgradePiston(EnvironmentHost host, boolean sticky) {
        super(host, "piston", "Piston upgrade", "Displacer II+", true);
        this.sticky = sticky;
        ((Component) node()).setVisibility(Visibility.Network);
    }

    protected final Direction direction(Arguments args) {
        int value = args.count() == 0 ? 3 : args.checkInteger(0);
        if (value < 0 || value > 5) throw new IllegalArgumentException("invalid side");
        Direction side = Direction.from3DDataValue(value);
        if (host instanceof Drone) return side;
        if (side != Direction.DOWN && side != Direction.UP && side != Direction.SOUTH)
            throw new IllegalArgumentException("unsupported side");
        return host instanceof Rotatable rotatable ? rotatable.toGlobal(side) : side;
    }

    @Callback(doc = "function():boolean -- Returns true if the piston is sticky, i.e. it can also pull.")
    public Object[] isSticky(Context context, Arguments args) { return new Object[]{sticky}; }

    @Callback(doc = "function([side:number]):boolean -- Tries to push the block on the specified side of the container of the upgrade. Defaults to front.")
    public Object[] push(Context context, Arguments args) { return action(context, direction(args), true); }

    protected Object[] action(Context context, Direction side, boolean extending) {
        BlockPos origin = position();
        if (side == Direction.DOWN && host instanceof Tablet tablet && tablet.player().getEyeHeight() > 1)
            origin = origin.below();
        var level = host.world();
        if (!extending) {
            var inside = level.getBlockState(origin.relative(side));
            if (!inside.isAir() && inside.getPistonPushReaction() != PushReaction.DESTROY)
                return new Object[]{false, "path is obstructed"};
        }
        PistonBaseBlock piston = (PistonBaseBlock) (sticky ? Blocks.STICKY_PISTON : Blocks.PISTON);
        if (!piston.moveBlocks(level, origin, side, extending)) return new Object[]{false, "move failed"};
        var sound = extending ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT;
        var packet = new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.BLOCKS,
                origin.getX(), origin.getY(), origin.getZ(), 1.5f, 1.0f, level.random.nextLong());
        for (var player : ((ServerLevel) level).players()) {
            if (player.distanceToSqr(origin.getX(), origin.getY(), origin.getZ()) <= 15 * 15)
                player.connection.send(packet);
        }
        context.pause(1.0 / 20.0);
        return new Object[]{true};
    }

    public static final class Sticky extends UpgradePiston {
        public Sticky(EnvironmentHost host) { super(host, true); }

        @Callback(doc = "function([side:number]):boolean -- Tries to reach out to the side given (default front) and pull a block similar to a vanilla sticky piston.")
        public Object[] pull(Context context, Arguments args) { return action(context, direction(args), false); }
    }
}
