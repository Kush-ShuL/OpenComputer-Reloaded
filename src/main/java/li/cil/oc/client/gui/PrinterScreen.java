package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.common.menu.PrinterMenu;
import li.cil.oc.server.component.Printer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Original 176x166 printer panel and three progress textures. */
public final class PrinterScreen extends AbstractContainerScreen<PrinterMenu> {
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/gui/" + name + ".png"); }
    private static final ResourceLocation BACKGROUND = texture("printer"), SLOT = texture("slot"), MATERIAL = texture("printer_material"), INK = texture("printer_ink"), PROGRESS = texture("printer_progress");
    public PrinterScreen(PrinterMenu menu, Inventory player, Component title) { super(menu, player, title); imageWidth = 176; imageHeight = 166; }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        for (var slot : menu.slots) graphics.blit(SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, 0, 0, 18, 18, 18, 18);
        bar(graphics, MATERIAL, 40, 21, 62, 12, (double) menu.material() / Printer.MAX_MATERIAL);
        bar(graphics, INK, 40, 53, 62, 12, (double) menu.ink() / Printer.MAX_INK);
        bar(graphics, PROGRESS, 105, 20, 46, 46, menu.progress());
        RenderSystem.disableBlend();
    }
    private void bar(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, double level) { int fill = (int) (width * Math.clamp(level, 0, 1)); if (fill > 0) graphics.blit(texture, leftPos + x, topPos + y, 0, 0, fill, height, width, height); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial); renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(40, 21, 62, 12, mouseX, mouseY)) graphics.renderTooltip(font, Component.literal(menu.material() + "/" + Printer.MAX_MATERIAL), mouseX, mouseY);
        if (isHovering(40, 53, 62, 12, mouseX, mouseY)) graphics.renderTooltip(font, Component.literal(menu.ink() + "/" + Printer.MAX_INK), mouseX, mouseY);
    }
}
