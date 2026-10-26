package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.common.inventory.ServerInventory;
import li.cil.oc.common.menu.ServerMenu;
import li.cil.oc.common.network.RackNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ServerScreen extends AbstractContainerScreen<ServerMenu> {
    private static ResourceLocation texture(String path){return ResourceLocation.fromNamespaceAndPath("opencomputers","textures/"+path+".png");}
    private static final ResourceLocation PANEL=texture("gui/background"),BOARD=texture("gui/server"),SLOT=texture("gui/slot"),POWER=texture("gui/button_power");
    private Button power;
    public ServerScreen(ServerMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=166;inventoryLabelY=72;}
    @Override protected void init(){super.init();power=addRenderableWidget(new Button(leftPos+48,topPos+33,18,18,Component.empty(),button->PacketDistributor.sendToServer(new RackNetworking.Edit(menu.position,menu.rackSlot,0,-1,2,!menu.running())),narration -> narration.get()){@Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partial){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();graphics.blit(POWER,getX(),getY(),menu.running()?18:0,isHoveredOrFocused()?18:0,18,18,36,36);RenderSystem.disableBlend();}});power.visible=menu.mounted;}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);graphics.blit(PANEL,leftPos,topPos,0,0,imageWidth,imageHeight);graphics.blit(BOARD,leftPos,topPos,0,0,176,82);var layout=ServerInventory.layout(menu.tier);for(int i=0;i<layout.length;i++){var slot=menu.slots.get(i);int x=leftPos+slot.x,y=topPos+slot.y;graphics.blit(SLOT,x-1,y-1,0,0,18,18,18,18);if(!slot.hasItem()){if(layout[i].tier()>=0&&layout[i].tier()<=2)graphics.blit(texture("icons/tier"+layout[i].tier()),x,y,0,0,16,16,16,16);graphics.blit(texture("icons/"+layout[i].slot()),x,y,0,0,16,16,16,16);}}RenderSystem.disableBlend();}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){super.render(graphics,mouseX,mouseY,partial);if(power.visible&&power.isMouseOver(mouseX,mouseY))graphics.renderTooltip(font,Component.translatable(menu.running()?"gui.opencomputers.turn_off":"gui.opencomputers.turn_on"),mouseX,mouseY);renderTooltip(graphics,mouseX,mouseY);}
}
