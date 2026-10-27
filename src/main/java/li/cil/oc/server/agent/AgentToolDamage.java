package li.cil.oc.server.agent;

import li.cil.oc.OpenComputers;
import li.cil.oc.api.event.RobotUsedToolEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** The original default tool repair runs inside the public ApplyDamageRate event. */
@EventBusSubscriber(modid=OpenComputers.ID)
public final class AgentToolDamage {
    private AgentToolDamage(){}
    @SubscribeEvent public static void apply(RobotUsedToolEvent.ApplyDamageRate event){
        if(!(event.agent instanceof li.cil.oc.api.internal.Robot)||event.toolAfterUse.isEmpty()||!event.toolAfterUse.isDamageableItem())return;
        int damage=event.toolAfterUse.getDamageValue()-event.toolBeforeUse.getDamageValue();
        if(damage>0){double actual=damage*event.getDamageRate();int repaired=event.agent.world().random.nextDouble()>.5?damage-(int)Math.floor(actual):damage-(int)Math.ceil(actual);event.toolAfterUse.setDamageValue(event.toolAfterUse.getDamageValue()-repaired);}
    }
}
