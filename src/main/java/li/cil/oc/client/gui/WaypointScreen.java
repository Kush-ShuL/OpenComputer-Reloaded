package li.cil.oc.client.gui;

import li.cil.oc.common.block.entity.WaypointBlockEntity;
import li.cil.oc.common.network.NetworkBlockNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** The original compact name field, with a server-validated label update on Enter. */
public final class WaypointScreen extends Screen {
    private static final ResourceLocation BACKGROUND=ResourceLocation.fromNamespaceAndPath("opencomputers","textures/gui/waypoint.png");
    private final NetworkBlockNetworking.WaypointOpen waypoint;
    private EditBox field;
    private int left,top;
    public WaypointScreen(NetworkBlockNetworking.WaypointOpen waypoint){super(Component.translatable("block.opencomputers.waypoint"));this.waypoint=waypoint;}
    @Override protected void init(){left=(width-176)/2;top=(height-24)/2;field=new EditBox(font,left+7,top+8,152,12,title);field.setMaxLength(32);field.setBordered(false);field.setTextColor(0xffffff);field.setValue(waypoint.label());field.setCanLoseFocus(false);addRenderableWidget(field);setInitialFocus(field);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){if(minecraft==null||minecraft.player==null||minecraft.level==null||!minecraft.level.dimension().location().equals(waypoint.dimension())||minecraft.player.distanceToSqr(waypoint.pos().getX()+.5,waypoint.pos().getY()+.5,waypoint.pos().getZ()+.5)>64||!(minecraft.level.getBlockEntity(waypoint.pos()) instanceof WaypointBlockEntity))onClose();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){if(!field.getValue().equals(waypoint.label()))PacketDistributor.sendToServer(new NetworkBlockNetworking.WaypointLabel(waypoint.dimension(),waypoint.pos(),field.getValue()));onClose();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){graphics.blit(BACKGROUND,left,top,0,0,176,24);super.render(graphics,mouseX,mouseY,partial);}
}
