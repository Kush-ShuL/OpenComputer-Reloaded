package li.cil.oc.server.agent;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.api.network.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.*;
import java.util.*;
import java.util.function.Consumer;

/** Inline persistent screen storage and host-owned synchronization for assembled robots. */
public final class AgentDisplayServices implements TextBuffer.HostServices {
    private final AgentHost agent;
    public AgentDisplayServices(AgentHost agent){this.agent=agent;}
    public boolean client(){return agent.world()!=null&&agent.world().isClientSide;}
    public void sendCommands(EnvironmentHost host,String address,List<TextBuffer.Command> commands){agent.synchronize();}
    public void requestInitialization(String address){if(client())li.cil.oc.client.AgentClient.sendInput("request");}
    public void register(EnvironmentHost host,String address,TextBuffer buffer){}
    public void unregister(EnvironmentHost host,TextBuffer buffer){}
    public void persistBuffer(EnvironmentHost host,CompoundTag metadata,String path,Consumer<CompoundTag> writer){CompoundTag data=new CompoundTag();writer.accept(data);metadata.put("buffer",data);}
    public CompoundTag loadBuffer(CompoundTag metadata,String path){return metadata.getCompound("buffer");}
    public Collection<Node> keyboardNodes(TextBuffer buffer){return agent.runtime().keyboards().stream().map(li.cil.oc.server.component.Keyboard::node).toList();}
    public void sendInput(String address,String type,Object...payload){if(client())li.cil.oc.client.AgentClient.sendInput(type,payload);}
    @OnlyIn(Dist.CLIENT)public boolean render(TextBuffer buffer,PoseStack pose,MultiBufferSource buffers,int light){return li.cil.oc.client.renderer.ScreenTextRenderer.draw(buffer,pose,buffers,light);}
    @OnlyIn(Dist.CLIENT)public int characterWidth(){return li.cil.oc.client.renderer.ScreenTextRenderer.characterWidth();}
    @OnlyIn(Dist.CLIENT)public int characterHeight(){return li.cil.oc.client.renderer.ScreenTextRenderer.characterHeight();}
}
