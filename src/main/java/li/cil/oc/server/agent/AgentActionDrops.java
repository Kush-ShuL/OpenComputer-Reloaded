package li.cil.oc.server.agent;

import java.util.ArrayList;
import java.util.List;
import li.cil.oc.OpenComputers;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Collects the real drops of one server-thread action, including hidden entity sections. */
@EventBusSubscriber(modid=OpenComputers.ID)
public final class AgentActionDrops implements AutoCloseable {
    private static final ThreadLocal<AgentActionDrops> CURRENT=new ThreadLocal<>();
    private final AgentHost host;
    private final AABB bounds;
    private final AgentActionDrops previous;
    private final List<ItemEntity> items=new ArrayList<>();
    private final List<ExperienceOrb> experience=new ArrayList<>();
    private boolean collected;
    private AgentActionDrops(AgentHost host,AABB bounds){this.host=host;this.bounds=bounds;previous=CURRENT.get();CURRENT.set(this);}
    public static AgentActionDrops begin(AgentHost host,AABB bounds){return new AgentActionDrops(host,bounds);}
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        AgentActionDrops capture=CURRENT.get();
        if(capture==null||event.getLevel()!=capture.host.world()||event.loadedFromDisk()||!capture.bounds.intersects(event.getEntity().getBoundingBox()))return;
        if(event.getEntity() instanceof ItemEntity item)capture.items.add(item);
        else if(event.getEntity() instanceof ExperienceOrb orb)capture.experience.add(orb);
    }
    public int experience(){return experience.stream().filter(orb->orb.isAddedToLevel()&&!orb.isRemoved()).mapToInt(ExperienceOrb::getValue).sum();}
    public void collect(){
        if(collected)return;collected=true;
        for(ItemEntity item:items)if(item.isAddedToLevel()&&!item.isRemoved()){
            item.setNoPickUpDelay();item.playerTouch(host.runtime().player());
        }
    }
    @Override public void close(){try{collect();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}}
}
