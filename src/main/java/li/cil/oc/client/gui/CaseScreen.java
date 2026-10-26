package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.common.InventorySlots;
import li.cil.oc.common.menu.CaseMenu;
import li.cil.oc.common.network.ComputerNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The original layered OC computer panel, component sockets and power button. */
@EventBusSubscriber(modid="opencomputers",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class CaseScreen extends AbstractContainerScreen<CaseMenu> {
    private static ResourceLocation texture(String path){return ResourceLocation.fromNamespaceAndPath("opencomputers","textures/"+path+".png");}
    private static final ResourceLocation PANEL=texture("gui/background");
    private static final ResourceLocation BOARD=texture("gui/computer");
    private static final ResourceLocation SOCKET=texture("gui/slot");
    private static final ResourceLocation POWER=texture("gui/button_power");
    private PowerButton powerButton;
    public CaseScreen(CaseMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=166+CaseMenu.HEADER_SPACE;inventoryLabelY=72+CaseMenu.HEADER_SPACE;}
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(CaseMenu.TYPE.get(),CaseScreen::new);}
    @Override protected void init(){super.init();powerButton=addRenderableWidget(new PowerButton(leftPos+70,topPos+33+CaseMenu.HEADER_SPACE));}
    @Override protected void containerTick(){super.containerTick();powerButton.setMessage(powerLabel());}
    private Component powerLabel(){return Component.translatable(menu.running()?"gui.opencomputers.turn_off":"gui.opencomputers.turn_on");}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        // computer.png contains just the circuit-board overlay, not the panel.
        graphics.blit(PANEL,leftPos,topPos,0,0,imageWidth,83);
        // Extend only the blank panel band; inventory slots keep their native pixel size.
        for(int y=0;y<CaseMenu.HEADER_SPACE;y++)graphics.blit(PANEL,leftPos,topPos+83+y,0,82,imageWidth,1);
        graphics.blit(PANEL,leftPos,topPos+83+CaseMenu.HEADER_SPACE,0,83,imageWidth,83);
        graphics.blit(BOARD,leftPos,topPos+CaseMenu.HEADER_SPACE,0,0,imageWidth,82);
        var specs=InventorySlots.computer[menu.tier];
        for(int index=0;index<specs.length;index++){
            Slot slot=menu.slots.get(index);int x=leftPos+slot.x,y=topPos+slot.y;
            graphics.blit(SOCKET,x-1,y-1,0,0,18,18,18,18);
            if(!slot.hasItem()){
                int tier=specs[index].tier();
                if(tier>=0&&tier<=2)graphics.blit(texture("icons/tier"+tier),x,y,0,0,16,16,16,16);
                graphics.blit(texture("icons/"+specs[index].slot()),x,y,0,0,16,16,16,16);
            }
        }
        RenderSystem.disableBlend();
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY){
        super.renderLabels(graphics,mouseX,mouseY);
        if(hoveredSlot!=null&&menu.getCarried().isEmpty()){
            int count=InventorySlots.computer[menu.tier].length;
            boolean hoveredComponent=hoveredSlot.index<count;
            for(Slot slot:menu.slots){
                boolean component=slot.index<count;
                if(component==hoveredComponent)continue;
                boolean eligible=component?hoveredSlot.hasItem()&&slot.mayPlace(hoveredSlot.getItem()):slot.hasItem()&&hoveredSlot.mayPlace(slot.getItem());
                if(eligible)graphics.fill(slot.x,slot.y,slot.x+16,slot.y+16,0x80FFFFFF);
            }
        }
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        super.render(graphics,mouseX,mouseY,partial);
        int controlY=topPos+CaseMenu.HEADER_SPACE;
        if(!menu.error().isEmpty())graphics.drawString(font,"!",leftPos+90,controlY+38,0xFF4444,false);
        if(powerButton.isMouseOver(mouseX,mouseY))graphics.renderTooltip(font,powerLabel(),mouseX,mouseY);
        else if(!menu.error().isEmpty()&&mouseX>=leftPos+89&&mouseX<leftPos+98&&mouseY>=controlY+33&&mouseY<controlY+51)graphics.renderTooltip(font,Component.translatable("oc:"+menu.error()),mouseX,mouseY);
        renderTooltip(graphics,mouseX,mouseY);
    }
    private final class PowerButton extends Button {
        PowerButton(int x,int y){super(x,y,18,18,powerLabel(),button->ComputerNetworking.setPower(menu.position,!menu.running()),DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partial){
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,alpha);
            graphics.blit(POWER,getX(),getY(),menu.running()?18:0,isHoveredOrFocused()?18:0,18,18,36,36);
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();
        }
    }
}
