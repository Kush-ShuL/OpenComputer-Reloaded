package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.common.menu.RaidMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Original three sockets and the localized, wrapped data-loss warning. */
public final class RaidScreen extends AbstractContainerScreen<RaidMenu> {
    private static final ResourceLocation BACKGROUND=ResourceLocation.fromNamespaceAndPath("opencomputers","textures/gui/raid.png");
    public RaidScreen(RaidMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=166;inventoryLabelY=-10000;}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);graphics.blit(BACKGROUND,leftPos,topPos,0,0,imageWidth,imageHeight);RenderSystem.disableBlend();}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){graphics.drawString(font,title,8,6,0x404040,false);Component warning=Component.literal(Component.translatable("oc:gui.Raid.Warning").getString().replace("[nl]","\n"));int lineY=46;for(var line:font.split(warning,imageWidth-16)){graphics.drawString(font,line,8,lineY,0x404040,false);lineY+=font.lineHeight;}}
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){super.render(graphics,x,y,partial);renderTooltip(graphics,x,y);}
}
