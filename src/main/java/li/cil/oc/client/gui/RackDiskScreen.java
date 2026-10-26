package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.common.menu.RackDiskMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Original disk-drive GUI coordinates and original native-resolution textures. */
public final class RackDiskScreen extends AbstractContainerScreen<RackDiskMenu> {
    private static ResourceLocation texture(String path){return ResourceLocation.fromNamespaceAndPath("opencomputers","textures/"+path+".png");}
    private static final ResourceLocation BACKGROUND=texture("gui/background"),SLOT=texture("gui/slot"),FLOPPY=texture("icons/floppy");
    public RackDiskScreen(RackDiskMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=166;}
    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partial){}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        graphics.blit(BACKGROUND,leftPos,topPos,0,0,imageWidth,imageHeight);
        graphics.blit(SLOT,leftPos+79,topPos+34,0,0,18,18,18,18);
        if(!menu.slots.getFirst().hasItem())graphics.blit(FLOPPY,leftPos+80,topPos+35,0,0,16,16,16,16);
        RenderSystem.disableBlend();
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){super.render(graphics,mouseX,mouseY,partial);renderTooltip(graphics,mouseX,mouseY);}
}
