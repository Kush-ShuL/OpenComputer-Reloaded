package li.cil.oc.common;

import li.cil.oc.api.Network;
import li.cil.oc.common.block.entity.CaseBlockEntity;
import li.cil.oc.server.machine.Machine;
import li.cil.oc.server.machine.Callbacks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** World lifecycle hooks shared by native and external OC components. */
public final class CommonEvents {
    private CommonEvents(){}
    @SubscribeEvent public static void neighbors(BlockEvent.NeighborNotifyEvent event){
        if(event.getLevel().isClientSide())return;
        if(event.getLevel().getBlockEntity(event.getPos())!=null)Network.joinOrCreateNetwork(event.getLevel(),event.getPos());
        for(var side:event.getNotifiedSides()){
            var pos=event.getPos().relative(side);if(event.getLevel().hasChunkAt(pos)&&event.getLevel().getBlockEntity(pos)!=null)Network.joinOrCreateNetwork(event.getLevel(),pos);
        }
    }
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event){
        if(event.getLevel().getBlockEntity(event.getPos()) instanceof CaseBlockEntity computer&&
            (!computer.canInteract(event.getPlayer())||computer.tier==3&&!event.getPlayer().getAbilities().instabuild))event.setCanceled(true);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){Machine.shutdownWorkers();Callbacks.clear();}
}
