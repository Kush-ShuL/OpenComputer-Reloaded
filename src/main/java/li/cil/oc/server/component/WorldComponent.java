package li.cil.oc.server.component;

import com.mojang.authlib.GameProfile;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import java.util.UUID;

/** Shared world access and original fake-player protection checks. */
abstract class WorldComponent extends AbstractManagedEnvironment {
    public final EnvironmentHost host;
    WorldComponent(EnvironmentHost host){this.host=host;}
    protected BlockPos position(){return BlockPos.containing(host.xPosition(),host.yPosition(),host.zPosition());}
    protected Direction side(Arguments args,int index){int value=args.checkInteger(index);if(value<0||value>5)throw new IllegalArgumentException("invalid side");Direction side=Direction.from3DDataValue(value);return host instanceof Rotatable rotatable?rotatable.toGlobal(side):side;}
    protected static int slot(Arguments args,int index,int count){int value=args.checkInteger(index)-1;if(value<0||value>=count)throw new IllegalArgumentException("invalid slot");return value;}
    protected static boolean defined(Arguments args,int index){return args.count()>index&&args.checkAny(index)!=null;}
    protected Player fakePlayer(){var player=FakePlayerFactory.get((ServerLevel)host.world(),new GameProfile(UUID.fromString(Settings.get().fakePlayerUuid),Settings.get().fakePlayerName));player.setPos(host.xPosition(),host.yPosition(),host.zPosition());return player;}
    protected boolean mayInteract(InventoryUtils.InventorySource source){
        Player player=fakePlayer();if(source.inventory() instanceof InvWrapper inventory&&!inventory.getInv().stillValid(player))return false;
        try{
            if(source instanceof InventoryUtils.BlockInventorySource block){Direction face=block.side()==null?Direction.UP:block.side();var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,block.position(),new BlockHitResult(Vec3.atCenterOf(block.position()),face,block.position(),false));NeoForge.EVENT_BUS.post(event);return !event.isCanceled()&&event.getUseBlock()!=TriState.FALSE;}
            if(source instanceof InventoryUtils.EntityInventorySource entity){var event=new PlayerInteractEvent.EntityInteract(player,InteractionHand.MAIN_HAND,entity.entity());NeoForge.EVENT_BUS.post(event);return !event.isCanceled();}
        }catch(Throwable error){OpenComputers.LOGGER.warn("An event handler failed while checking inventory access",error);}return true;
    }
}
