package li.cil.oc.common.capabilities;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.*;
import li.cil.oc.server.machine.Callbacks;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.*;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/** NeoForge attachment/lifecycle replacement for the original SimpleComponent ASM. */
public final class SimpleComponentBridge {
    private SimpleComponentBridge(){}
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS=DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,"opencomputers");
    private static final Supplier<AttachmentType<State>> STATE=ATTACHMENTS.register("simple_environment",()->AttachmentType.serializable(State::new).build());
    private static final CopyOnWriteArrayList<WeakReference<State>> ACTIVE=new CopyOnWriteArrayList<>();
    public static void register(IEventBus bus){ATTACHMENTS.register(bus);}
    public static Environment environment(BlockEntity entity){
        if(!(entity instanceof SimpleComponent)||entity.getClass().isAnnotationPresent(SimpleComponent.SkipInjection.class))return null;
        return entity.getData(STATE);
    }
    public static final class State implements Environment,Callbacks.CompositeEnvironment,INBTSerializable<CompoundTag> {
        private final BlockEntity owner;
        private Node node;
        private CompoundTag savedNode;
        private State(IAttachmentHolder holder){
            if(!(holder instanceof BlockEntity entity)||!(entity instanceof SimpleComponent))throw new IllegalArgumentException("Simple components require a block entity");
            owner=entity;ACTIVE.add(new WeakReference<>(this));
        }
        @Override public Node node(){
            if(node==null&&owner.getLevel()!=null&&!owner.getLevel().isClientSide){
                node=Network.newNode(this,Visibility.Network).withComponent(((SimpleComponent)owner).getComponentName()).create();
                if(savedNode!=null){node.load(savedNode);savedNode=null;}
            }
            return node;
        }
        @Override public Collection<?> callbackEnvironments(){return List.of(owner);}
        @Override public void onConnect(Node node){}
        @Override public void onDisconnect(Node node){}
        @Override public void onMessage(Message message){}
        @Override public CompoundTag serializeNBT(HolderLookup.Provider registries){
            CompoundTag tag=new CompoundTag();if(node!=null)node.save(tag);else if(savedNode!=null)tag=savedNode.copy();return tag;
        }
        @Override public void deserializeNBT(HolderLookup.Provider registries,CompoundTag tag){if(node!=null)node.load(tag);else savedNode=tag.copy();}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        for(WeakReference<State> reference:ACTIVE){State state=reference.get();if(state==null){ACTIVE.remove(reference);continue;}
            var world=state.owner.getLevel();
            if(world==null||world.isClientSide)continue;
            if(state.owner.isRemoved()||!world.hasChunkAt(state.owner.getBlockPos())||world.getBlockEntity(state.owner.getBlockPos())!=state.owner){if(state.node!=null)state.node.remove();ACTIVE.remove(reference);}
            else if(state.node()!=null&&state.node.network()==null)Network.joinOrCreateNetwork(state.owner);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){for(var reference:ACTIVE){State state=reference.get();if(state!=null&&state.node!=null)state.node.remove();}ACTIVE.clear();}
}
