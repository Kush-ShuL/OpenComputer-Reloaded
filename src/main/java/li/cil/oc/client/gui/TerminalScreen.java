package li.cil.oc.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import li.cil.oc.Settings;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.client.renderer.ScreenRenderTypes;
import li.cil.oc.client.renderer.ScreenTextRenderer;
import li.cil.oc.common.component.ClientGpuTextBufferHandler;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.common.network.RackNetworking;
import li.cil.oc.common.network.ScreenNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** The real terminal buffer, with the same font/border/input coordinates as a physical screen. */
public final class TerminalScreen extends Screen {
    private static final ResourceLocation BORDER=ResourceLocation.fromNamespaceAndPath("opencomputers","textures/gui/borders.png");
    private final UUID session;
    public final String serverAddress;
    private final ResourceLocation dimension;
    private final TextBuffer buffer;
    private final Map<Integer,Character> pressed=new HashMap<>();
    private long revision;
    private float left,top,scale=1;
    private int textWidth,textHeight,lastCode;
    private boolean dragging;
    public TerminalScreen(RackNetworking.TerminalOpen packet){super(Component.translatable("item.opencomputers.terminal"));session=packet.token();serverAddress=packet.address();dimension=packet.dimension();
        EnvironmentHost host=new EnvironmentHost(){public Level world(){return Minecraft.getInstance().level;}public double xPosition(){return packet.position().getX()+.5;}public double yPosition(){return packet.position().getY()+.5;}public double zPosition(){return packet.position().getZ()+.5;}public void markChanged(){}};
        buffer=new TextBuffer(host,new Services());buffer.setMaximumResolution(Settings.screenResolutionsByTier[2][0],Settings.screenResolutionsByTier[2][1]);buffer.setMaximumColorDepth(Settings.screenDepthsByTier[2]);applySnapshot(packet.data(),packet.revision());
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partial){}
    @Override public void tick(){if(minecraft==null||minecraft.level==null||minecraft.player==null||!minecraft.level.dimension().location().equals(dimension)){onClose();return;}buffer.update();}
    public void request(){if(Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new RackNetworking.TerminalRequest(session));}
    public void update(RackNetworking.TerminalUpdate packet){if(!packet.token().equals(session))return;if(packet.data().getBoolean("closed")){onClose();return;}if(packet.full()){if(packet.revision()>=revision)applySnapshot(packet.data(),packet.revision());return;}if(packet.revision()<=revision)return;if(packet.revision()!=revision+1){request();return;}
        for(Tag entry:packet.data().getList("commands",Tag.TAG_COMPOUND)){CompoundTag command=(CompoundTag)entry;Object[] p=ScreenNetworking.commandValues(command);switch(command.getString("type")){
            case "power"->buffer.applyPowerState((Boolean)p[0]);case "maxResolution"->buffer.setMaximumResolution((Integer)p[0],(Integer)p[1]);case "resolution"->buffer.setResolution((Integer)p[0],(Integer)p[1]);case "viewport"->buffer.setViewport((Integer)p[0],(Integer)p[1]);case "depth"->buffer.setColorDepth((li.cil.oc.api.internal.TextBuffer.ColorDepth)p[0]);case "palette"->buffer.setPaletteColor((Integer)p[0],(Integer)p[1]);
            case "color"->{var foreground=(li.cil.oc.util.PackedColor.Color)p[0];var background=(li.cil.oc.util.PackedColor.Color)p[1];buffer.setForegroundColor(foreground.value(),foreground.isPalette());buffer.setBackgroundColor(background.value(),background.isPalette());}
            case "copy"->buffer.copy((Integer)p[0],(Integer)p[1],(Integer)p[2],(Integer)p[3],(Integer)p[4],(Integer)p[5]);case "fill"->buffer.fill((Integer)p[0],(Integer)p[1],(Integer)p[2],(Integer)p[3],(Integer)p[4]);case "set"->buffer.set((Integer)p[0],(Integer)p[1],(String)p[2],(Boolean)p[3]);
            case "ramInit"->buffer.loadBuffer((String)p[0],(Integer)p[1],(CompoundTag)p[2]);case "ramDestroy"->buffer.removeBuffer((String)p[0],(Integer)p[1]);case "bitblt"->ClientGpuTextBufferHandler.bitblt(buffer,(Integer)p[0],(Integer)p[1],(Integer)p[2],(Integer)p[3],(String)p[4],(Integer)p[5],(Integer)p[6],(Integer)p[7]);case "rawText"->buffer.rawSetText((Integer)p[0],(Integer)p[1],(int[][])p[2]);case "rawForeground"->buffer.rawSetForeground((Integer)p[0],(Integer)p[1],(int[][])p[2]);case "rawBackground"->buffer.rawSetBackground((Integer)p[0],(Integer)p[1],(int[][])p[2]);default->throw new IllegalArgumentException("Unknown terminal command");}}
        revision=packet.revision();buffer.markInitialized();buffer.dirty=true;
    }
    private void applySnapshot(CompoundTag tag,long sequence){buffer.data().load(tag);buffer.videoDevices().clear();for(Tag entry:tag.getList("vram",Tag.TAG_COMPOUND)){CompoundTag page=(CompoundTag)entry;buffer.loadBuffer(page.getString("owner"),page.getInt("id"),page);}buffer.setViewport(Math.clamp(tag.getInt("viewportWidth"),1,buffer.getWidth()),Math.clamp(tag.getInt("viewportHeight"),1,buffer.getHeight()));buffer.setPowerState(tag.getBoolean("on"));buffer.setRenderingEnabled(tag.getBoolean("render"));buffer.markInitialized();buffer.dirty=true;revision=sequence;}
    public void forwardInput(String type,Object... payload){if(type.equals("keyboard.keyDown")||type.equals("keyboard.keyUp"))send(type.equals("keyboard.keyDown")?ScreenNetworking.InputType.KEY_DOWN:ScreenNetworking.InputType.KEY_UP,(Character)payload[1],(Integer)payload[2],0,0,0,"");else if(type.equals("keyboard.clipboard"))send(ScreenNetworking.InputType.CLIPBOARD,0,0,0,0,0,(String)payload[1]);}
    private void layout(){textWidth=Math.max(1,buffer.renderWidth());textHeight=Math.max(1,buffer.renderHeight());float margin=Math.min(16F,Math.min(width,height)/4F);scale=Math.min(Math.max(1F,width-margin*2)/(textWidth+16F),Math.max(1F,height-margin*2)/(textHeight+16F));left=(width-(textWidth+16F)*scale)/2;top=(height-(textHeight+16F)*scale)/2;}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){super.render(graphics,mouseX,mouseY,partial);layout();graphics.pose().pushPose();graphics.pose().translate(left,top,0);graphics.pose().scale(scale,scale,1);float[] x={0,7,9+textWidth,16+textWidth},y={0,7,9+textHeight,16+textHeight},uv={0,7/16f,9/16f,1};var vertices=graphics.bufferSource().getBuffer(ScreenRenderTypes.texture(BORDER,false));for(int row=0;row<3;row++)for(int col=0;col<3;col++){float u0=col==1?7.25f/16:uv[col],u1=col==1?8.75f/16:uv[col+1],v0=row==1?7.25f/16:uv[row],v1=row==1?8.75f/16:uv[row+1];vertices.addVertex(graphics.pose().last(),x[col],y[row+1],0).setColor(-1).setUv(u0,v1).setLight(LightTexture.FULL_BRIGHT);vertices.addVertex(graphics.pose().last(),x[col+1],y[row+1],0).setColor(-1).setUv(u1,v1).setLight(LightTexture.FULL_BRIGHT);vertices.addVertex(graphics.pose().last(),x[col+1],y[row],0).setColor(-1).setUv(u1,v0).setLight(LightTexture.FULL_BRIGHT);vertices.addVertex(graphics.pose().last(),x[col],y[row],0).setColor(-1).setUv(u0,v0).setLight(LightTexture.FULL_BRIGHT);}graphics.pose().pushPose();graphics.pose().translate(8,8,0);if(buffer.isRenderingEnabled())buffer.renderText(graphics.pose(),graphics.bufferSource(),LightTexture.FULL_BRIGHT);graphics.pose().popPose();graphics.flush();graphics.pose().popPose();}
    private void send(ScreenNetworking.InputType action,int character,int code,double x,double y,int value,String text){if(Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new RackNetworking.TerminalInput(session,action,character,code,x,y,value,text));}
    @Override public boolean keyPressed(int key,int scancode,int modifiers){if(key==GLFW.GLFW_KEY_ESCAPE||key==GLFW.GLFW_KEY_F11)return super.keyPressed(key,scancode,modifiers);if(Screen.isPaste(key)){paste();return true;}lastCode=LegacyKeyCodes.of(key);if(!LegacyKeyCodes.printable(key)||Screen.hasControlDown()||Screen.hasAltDown()){char character=Screen.hasControlDown()&&key>=GLFW.GLFW_KEY_A&&key<=GLFW.GLFW_KEY_Z?(char)(key-GLFW.GLFW_KEY_A+1):LegacyKeyCodes.controlCharacter(key);pressed.put(lastCode,character);send(ScreenNetworking.InputType.KEY_DOWN,character,lastCode,0,0,0,"");}return true;}
    @Override public boolean charTyped(char character,int modifiers){pressed.put(lastCode,character);send(ScreenNetworking.InputType.KEY_DOWN,character,lastCode,0,0,0,"");return true;}
    @Override public boolean keyReleased(int key,int scancode,int modifiers){int code=LegacyKeyCodes.of(key);Character character=pressed.remove(code);if(character!=null)send(ScreenNetworking.InputType.KEY_UP,character,code,0,0,0,"");return true;}
    private void paste(){String text=minecraft.keyboardHandler.getClipboard();for(int start=0;start<text.length();){int end=Math.min(start+8192,text.length());if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;send(ScreenNetworking.InputType.CLIPBOARD,0,0,0,0,0,text.substring(start,end));start=end;}}
    private double[] point(double x,double y){layout();double bx=((x-left)/scale-8)/ScreenTextRenderer.characterWidth(),by=((y-top)/scale-8)/ScreenTextRenderer.characterHeight();return bx>=0&&by>=0&&bx<buffer.getViewportWidth()&&by<buffer.getViewportHeight()?new double[]{bx,by}:null;}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==2){paste();return true;}double[] point=point(x,y);if(point!=null&&button>=0&&button<2){dragging=true;send(ScreenNetworking.InputType.TOUCH,0,0,point[0],point[1],button,"");return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){double[] point=point(x,y);if(dragging&&point!=null){send(ScreenNetworking.InputType.DRAG,0,0,point[0],point[1],button,"");return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){double[] point=point(x,y);if(dragging){if(point!=null)send(ScreenNetworking.InputType.DROP,0,0,point[0],point[1],button,"");dragging=false;return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){double[] point=point(x,y);if(point!=null&&dy!=0){send(ScreenNetworking.InputType.SCROLL,0,0,point[0],point[1],dy>0?1:-1,"");return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public void removed(){for(var key:pressed.entrySet())send(ScreenNetworking.InputType.KEY_UP,key.getValue(),key.getKey(),0,0,0,"");pressed.clear();send(ScreenNetworking.InputType.CLOSE,0,0,0,0,0,"");super.removed();}
    private final class Services implements TextBuffer.HostServices {
        public boolean client(){return true;}public void sendCommands(EnvironmentHost host,String address,List<TextBuffer.Command> commands){}public void requestInitialization(String address){request();}public void register(EnvironmentHost host,String address,TextBuffer value){}public void unregister(EnvironmentHost host,TextBuffer value){}
        public void persistBuffer(EnvironmentHost host,CompoundTag metadata,String path,Consumer<CompoundTag> writer){}public CompoundTag loadBuffer(CompoundTag metadata,String path){return metadata.getCompound("buffer");}public Collection<Node> keyboardNodes(TextBuffer value){return List.of();}
        public void sendInput(String address,String type,Object... payload){if(type.equals("keyboard.keyDown")||type.equals("keyboard.keyUp"))send(type.equals("keyboard.keyDown")?ScreenNetworking.InputType.KEY_DOWN:ScreenNetworking.InputType.KEY_UP,(Character)payload[1],(Integer)payload[2],0,0,0,"");else if(type.equals("keyboard.clipboard"))send(ScreenNetworking.InputType.CLIPBOARD,0,0,0,0,0,(String)payload[1]);}
        public boolean render(TextBuffer value,PoseStack pose,MultiBufferSource buffers,int light){return ScreenTextRenderer.draw(value,pose,buffers,light);}public int characterWidth(){return ScreenTextRenderer.characterWidth();}public int characterHeight(){return ScreenTextRenderer.characterHeight();}
    }
}
