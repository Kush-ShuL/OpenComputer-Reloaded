package li.cil.oc.common.component;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.common.network.RackNetworking;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Explicit persistence and authenticated network services for the real terminal buffer. */
public final class TerminalServices implements TextBuffer.HostServices {
    private final TerminalServer terminal;
    public TerminalServices(TerminalServer terminal){this.terminal=terminal;}
    @Override public boolean client(){return terminal.world().isClientSide;}
    @Override public void sendCommands(EnvironmentHost host,String address,List<TextBuffer.Command> commands){if(!client())RackNetworking.publishTerminal(terminal,commands);}
    @Override public void requestInitialization(String address){if(client())li.cil.oc.client.RackClient.requestTerminal(terminal.address());}
    @Override public void register(EnvironmentHost host,String address,TextBuffer buffer){if(!client())RackNetworking.registerTerminal(terminal);}
    @Override public void unregister(EnvironmentHost host,TextBuffer buffer){if(!client())RackNetworking.unregisterTerminal(terminal);}
    @Override public void persistBuffer(EnvironmentHost host,CompoundTag metadata,String path,Consumer<CompoundTag> writer){CompoundTag data=new CompoundTag();writer.accept(data);metadata.put("buffer",data);}
    @Override public CompoundTag loadBuffer(CompoundTag metadata,String path){return metadata.getCompound("buffer");}
    @Override public Collection<Node> keyboardNodes(TextBuffer buffer){Node keyboard=terminal.keyboard().node();return keyboard==null?List.of():List.of(keyboard);}
    @Override public void sendInput(String address,String type,Object... payload){if(client())li.cil.oc.client.RackClient.sendTerminalInput(terminal.address(),type,payload);}
    @OnlyIn(Dist.CLIENT) @Override public boolean render(TextBuffer buffer,PoseStack pose,MultiBufferSource buffers,int light){return li.cil.oc.client.renderer.ScreenTextRenderer.draw(buffer,pose,buffers,light);}
    @Override public int characterWidth(){return client()?li.cil.oc.client.renderer.ScreenTextRenderer.characterWidth():4;}
    @Override public int characterHeight(){return client()?li.cil.oc.client.renderer.ScreenTextRenderer.characterHeight():8;}
}
