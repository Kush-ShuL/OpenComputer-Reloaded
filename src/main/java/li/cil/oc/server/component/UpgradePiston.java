package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.internal.*;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.EnvironmentHost;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.*;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.*;

/** Uses the vanilla piston resolver/mover, including adhesion and the twelve-block limit. */
public class UpgradePiston extends UpgradeBase {
    private final boolean sticky;
    public UpgradePiston(EnvironmentHost host,boolean sticky){super(host,"piston","Piston upgrade","Displacer II+",true);this.sticky=sticky;}
    private Direction direction(Arguments args){int value=args.optInteger(0,3);if(value<0||value>5||host instanceof li.cil.oc.api.internal.Robot&&value!=0&&value!=1&&value!=3)throw new IllegalArgumentException("invalid side");Direction side=Direction.from3DDataValue(value);return host instanceof li.cil.oc.api.internal.Drone?side:host instanceof Rotatable rotatable?rotatable.toGlobal(side):side;}
    @Callback public Object[] isSticky(Context context,Arguments args){return new Object[]{sticky};}
    @Callback public Object[] push(Context context,Arguments args){return action(context,direction(args),true);}
    protected Object[] action(Context context,Direction side,boolean extending){BlockPos origin=position();if(side==Direction.DOWN&&host instanceof Tablet tablet&&tablet.player().getEyeHeight()>1)origin=origin.below();var level=host.world();if(!extending){var inside=level.getBlockState(origin.relative(side));if(!inside.isAir()&&inside.getPistonPushReaction()!=PushReaction.DESTROY)return new Object[]{false,"path is obstructed"};}
        var resolver=new PistonStructureResolver(level,origin,side,extending);if(!resolver.resolve())return new Object[]{false,"move failed"};Set<BlockPos> affected=new HashSet<>();affected.addAll(resolver.getToPush());affected.addAll(resolver.getToDestroy());for(BlockPos pos:resolver.getToPush())affected.add(pos.relative(extending?side:side.getOpposite()));for(BlockPos pos:affected){if(!level.hasChunkAt(pos)||!level.mayInteract(fakePlayer(),pos))return new Object[]{false,"not allowed"};var event=new BlockEvent.BreakEvent(level,pos,level.getBlockState(pos),fakePlayer());NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return new Object[]{false,"not allowed"};}if(!connector().tryChangeBuffer(-Settings.get().pistonCost))return new Object[]{null,"not enough energy"};PistonBaseBlock piston=(PistonBaseBlock)(sticky?Blocks.STICKY_PISTON:Blocks.PISTON);if(!piston.moveBlocks(level,origin,side,extending)){connector().changeBuffer(Settings.get().pistonCost);return new Object[]{false,"move failed"};}level.playSound(null,origin,extending?SoundEvents.PISTON_EXTEND:SoundEvents.PISTON_CONTRACT,SoundSource.BLOCKS,.5f,level.random.nextFloat()*.25f+.6f);context.pause(.05);return new Object[]{true};}
    public static final class Sticky extends UpgradePiston {public Sticky(EnvironmentHost host){super(host,true);}@Callback public Object[] pull(Context context,Arguments args){return action(context,directionForPull(args),false);}private Direction directionForPull(Arguments args){int value=args.optInteger(0,3);if(value<0||value>5||host instanceof li.cil.oc.api.internal.Robot&&value!=0&&value!=1&&value!=3)throw new IllegalArgumentException("invalid side");Direction side=Direction.from3DDataValue(value);return host instanceof li.cil.oc.api.internal.Drone?side:host instanceof Rotatable rotatable?rotatable.toGlobal(side):side;}}
}
