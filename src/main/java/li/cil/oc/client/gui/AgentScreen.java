package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.common.menu.AgentMenu;
import li.cil.oc.common.network.AgentNetworking;
import li.cil.oc.common.entity.DroneEntity;
import li.cil.oc.server.agent.RobotController;
import li.cil.oc.client.renderer.ScreenTextRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Original robot/drone inventory textures and embedded robot terminal input. */
public final class AgentScreen extends AbstractContainerScreen<AgentMenu> {
    private static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("opencomputers","textures/gui/"+name+".png");}
    private final Map<Integer,Character> pressed=new HashMap<>();
    private int lastCode;
    private PowerButton power;
    private float textX,textY,textScale;
    public AgentScreen(AgentMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=menu.robot?256:176;imageHeight=menu.robot?256-menu.deltaY:148;titleLabelY=-10000;inventoryLabelY=-10000;}
    private RobotController robot(){return menu.host instanceof RobotController robot?robot:null;}
    private boolean running(){return menu.host instanceof DroneEntity drone?drone.running():robot()!=null&&robot().proxy.clientRunning;}
    private void send(AgentNetworking.Action action,int character,int code,double x,double y,int value,String text){PacketDistributor.sendToServer(new AgentNetworking.Input(menu.containerId,action,character,code,x,y,value,text));}
    @Override protected void init(){super.init();power=addRenderableWidget(new PowerButton(leftPos+(menu.robot?5:7),topPos+(menu.robot?153-menu.deltaY:45)));}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);graphics.blit(texture(menu.robot?menu.hasScreen?"robot":"robot_noscreen":"drone"),leftPos,topPos,0,0,imageWidth,imageHeight,256,256);RenderSystem.disableBlend();
        if(robot()!=null&&robot().clientScreen!=null){var buffer=robot().clientScreen;int w=buffer.renderWidth(),h=buffer.renderHeight();textScale=Math.min(240f/Math.max(1,w),140f/Math.max(1,h));textX=leftPos+8+(240-w*textScale)/2;textY=topPos+8+(140-h*textScale)/2;graphics.pose().pushPose();graphics.pose().translate(textX,textY,0);graphics.pose().scale(textScale,textScale,1);buffer.renderText(graphics.pose(),graphics.bufferSource(),LightTexture.FULL_BRIGHT);graphics.pose().popPose();graphics.flush();}
        else if(menu.host instanceof DroneEntity drone){int y=topPos+9;for(String line:drone.statusText().split("\n",-1)){graphics.drawString(font,line,leftPos+9,y,0x66DD55,false);y+=font.lineHeight;}}
        double energy=robot()!=null?robot().info.totalEnergy:((DroneEntity)menu.host).energy(),maximum=menu.robot?li.cil.oc.Settings.get().bufferRobot:((DroneEntity)menu.host).energyCapacity();int barWidth=(int)(108*Math.clamp(energy/Math.max(1,maximum),0,1));int bx=leftPos+(menu.robot?26:28),by=topPos+(menu.robot?156-menu.deltaY:48);graphics.fill(bx,by,bx+barWidth,by+12,0xFF55AA66);
        if(menu.robot){int selection=menu.host.selectedSlot()-menu.scroll();if(selection>=0&&selection<16){int x=leftPos+169+18*(selection%4),y=topPos+155-menu.deltaY+18*(selection/4);graphics.fill(x,y,x+18,y+1,0xFF55FF55);graphics.fill(x,y+17,x+18,y+18,0xFF55FF55);graphics.fill(x,y,x+1,y+18,0xFF55FF55);graphics.fill(x+17,y,x+18,y+18,0xFF55FF55);}}
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){super.render(graphics,mouseX,mouseY,partial);renderTooltip(graphics,mouseX,mouseY);if(power.isMouseOver(mouseX,mouseY))graphics.renderTooltip(font,Component.translatableWithFallback(running()?"gui.opencomputers.turn_off":"gui.opencomputers.turn_on",running()?"Turn off":"Turn on"),mouseX,mouseY);}
    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partial){}
    private boolean keyboard(){return robot()!=null&&robot().clientScreen!=null&&robot().clientHasKeyboard;}
    @Override public boolean keyPressed(int key,int scancode,int modifiers){if(key==GLFW.GLFW_KEY_ESCAPE||!keyboard())return super.keyPressed(key,scancode,modifiers);if(Screen.isPaste(key)){String text=minecraft.keyboardHandler.getClipboard();for(int start=0;start<text.length();){int end=Math.min(start+8192,text.length());if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;send(AgentNetworking.Action.CLIPBOARD,0,0,0,0,0,text.substring(start,end));start=end;}return true;}lastCode=LegacyKeyCodes.of(key);if(!LegacyKeyCodes.printable(key)||Screen.hasControlDown()||Screen.hasAltDown()){char character=Screen.hasControlDown()&&key>=GLFW.GLFW_KEY_A&&key<=GLFW.GLFW_KEY_Z?(char)(key-GLFW.GLFW_KEY_A+1):LegacyKeyCodes.controlCharacter(key);pressed.put(lastCode,character);send(AgentNetworking.Action.KEY_DOWN,character,lastCode,0,0,0,"");}return true;}
    @Override public boolean charTyped(char character,int modifiers){if(!keyboard())return super.charTyped(character,modifiers);pressed.put(lastCode,character);send(AgentNetworking.Action.KEY_DOWN,character,lastCode,0,0,0,"");return true;}
    @Override public boolean keyReleased(int key,int scancode,int modifiers){Character character=pressed.remove(LegacyKeyCodes.of(key));if(character!=null){send(AgentNetworking.Action.KEY_UP,character,LegacyKeyCodes.of(key),0,0,0,"");return true;}return super.keyReleased(key,scancode,modifiers);}
    private double[] coordinates(double x,double y){if(robot()==null||robot().clientScreen==null||textScale<=0)return null;double column=(x-textX)/textScale/ScreenTextRenderer.characterWidth(),row=(y-textY)/textScale/ScreenTextRenderer.characterHeight();return column>=0&&row>=0&&column<robot().clientScreen.getViewportWidth()&&row<robot().clientScreen.getViewportHeight()?new double[]{column,row}:null;}
    @Override public boolean mouseClicked(double x,double y,int button){double[] point=coordinates(x,y);if(point!=null&&button>=0&&button<2){send(AgentNetworking.Action.TOUCH,0,0,point[0],point[1],button,"");return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){double[] point=coordinates(x,y);if(point!=null&&button>=0&&button<2){send(AgentNetworking.Action.DRAG,0,0,point[0],point[1],button,"");return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){double[] point=coordinates(x,y);if(point!=null&&button>=0&&button<2){send(AgentNetworking.Action.DROP,0,0,point[0],point[1],button,"");return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(menu.robot&&x>=leftPos+169&&x<leftPos+249&&y>=topPos+155-menu.deltaY){int row=menu.scroll()/4+(dy>0?-1:1);menu.scroll(row);send(AgentNetworking.Action.SCROLL,0,0,0,0,row,"");return true;}double[] point=coordinates(x,y);if(point!=null){send(AgentNetworking.Action.MOUSE_SCROLL,0,0,point[0],point[1],dy>0?1:-1,"");return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public void removed(){for(var key:pressed.entrySet())send(AgentNetworking.Action.KEY_UP,key.getValue(),key.getKey(),0,0,0,"");pressed.clear();super.removed();}
    private final class PowerButton extends Button{
        PowerButton(int x,int y){super(x,y,18,18,Component.literal("Power"),button->send(AgentNetworking.Action.POWER,0,0,0,0,0,""),DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partial){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();graphics.blit(texture("button_power"),getX(),getY(),running()?18:0,isHoveredOrFocused()?18:0,18,18,36,36);RenderSystem.disableBlend();}
    }
}
