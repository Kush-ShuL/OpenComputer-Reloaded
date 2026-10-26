package li.cil.oc.server.portable;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.api.network.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.neoforged.api.distmarker.*;

public final class TabletDisplayServices implements TextBuffer.HostServices {
    private final TabletHost tablet;
    public TabletDisplayServices(TabletHost tablet){this.tablet=tablet;}
    public boolean client(){return false;}
    public void sendCommands(EnvironmentHost host,String address,List<TextBuffer.Command> commands){li.cil.oc.common.network.PortableNetworking.publishCommands(tablet,commands);tablet.markChanged();}
    public void requestInitialization(String address){}
    public void register(EnvironmentHost host,String address,TextBuffer buffer){}
    public void unregister(EnvironmentHost host,TextBuffer buffer){}
    public void persistBuffer(EnvironmentHost host,CompoundTag metadata,String path,Consumer<CompoundTag> writer){CompoundTag tag=new CompoundTag();writer.accept(tag);metadata.put("buffer",tag);}
    public CompoundTag loadBuffer(CompoundTag metadata,String path){return metadata.getCompound("buffer");}
    public Collection<Node> keyboardNodes(TextBuffer buffer){return tablet.runtime.keyboards().stream().map(li.cil.oc.server.component.Keyboard::node).toList();}
    public void sendInput(String address,String type,Object...payload){}
    @OnlyIn(Dist.CLIENT)public boolean render(TextBuffer buffer,PoseStack pose,MultiBufferSource buffers,int light){return li.cil.oc.client.renderer.ScreenTextRenderer.draw(buffer,pose,buffers,light);}
    public int characterWidth(){return 4;}
    public int characterHeight(){return 8;}
}
