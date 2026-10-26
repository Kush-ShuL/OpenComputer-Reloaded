package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.common.menu.ChargerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class ChargerScreen extends AbstractContainerScreen<ChargerMenu> {
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/" + name + ".png"); }
    private static final ResourceLocation BACKGROUND = texture("gui/background"), SLOT = texture("gui/slot"), TABLET = texture("icons/tablet");
    public ChargerScreen(ChargerMenu menu, Inventory player, Component title) { super(menu, player, title); imageWidth = 176; imageHeight = 166; }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        for (var slot : menu.slots) graphics.blit(SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, 0, 0, 18, 18, 18, 18);
        if (!menu.slots.getFirst().hasItem()) graphics.blit(TABLET, leftPos + 80, topPos + 35, 0, 0, 16, 16, 16, 16);
        RenderSystem.disableBlend();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) { super.render(graphics, mouseX, mouseY, partial); renderTooltip(graphics, mouseX, mouseY); }
}
