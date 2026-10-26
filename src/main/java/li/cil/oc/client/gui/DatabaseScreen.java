package li.cil.oc.client.gui;

import li.cil.oc.common.menu.DatabaseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class DatabaseScreen extends AbstractContainerScreen<DatabaseMenu> {
    public DatabaseScreen(DatabaseMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=256;titleLabelY=-10000;inventoryLabelY=-10000;}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){for(int layer=0;layer<=menu.tier;layer++){com.mojang.blaze3d.systems.RenderSystem.enableBlend();graphics.blit(ResourceLocation.fromNamespaceAndPath("opencomputers","textures/gui/database"+(layer==0?"":layer)+".png"),leftPos,topPos,0,0,imageWidth,imageHeight,256,256);com.mojang.blaze3d.systems.RenderSystem.disableBlend();}}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){super.render(graphics,mouseX,mouseY,partial);renderTooltip(graphics,mouseX,mouseY);}
    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partial){}
}
