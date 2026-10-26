package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.common.menu.MicrocontrollerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;

public final class MicrocontrollerScreen extends AbstractContainerScreen<MicrocontrollerMenu> {
    private static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("opencomputers","textures/"+name+".png");}
    private static final ResourceLocation BACKGROUND=texture("gui/background"),SLOT=texture("gui/slot"),EEPROM=texture("icons/eeprom");
    public MicrocontrollerScreen(MicrocontrollerMenu menu,Inventory player,Component title){super(menu,player,title);imageWidth=176;imageHeight=166;inventoryLabelY=72;}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();graphics.blit(BACKGROUND,leftPos,topPos,0,0,imageWidth,imageHeight);for(var slot:menu.slots)graphics.blit(SLOT,leftPos+slot.x-1,topPos+slot.y-1,0,0,18,18,18,18);if(!menu.slots.getFirst().hasItem())graphics.blit(EEPROM,leftPos+80,topPos+35,0,0,16,16,16,16);RenderSystem.disableBlend();}
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){super.render(graphics,x,y,partial);renderTooltip(graphics,x,y);}
}
