package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import li.cil.oc.common.menu.WorkstationMenu;
import li.cil.oc.common.template.AssemblerTemplates;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Original production layouts; tooltips depend on pointer position, not retained button focus. */
public final class WorkstationScreen extends AbstractContainerScreen<WorkstationMenu> {
    private static final ResourceLocation ASSEMBLER = texture("gui/robot_assembler"), DISASSEMBLER = texture("gui/disassembler"), SLOT = texture("gui/slot"), RUN = texture("gui/button_run");
    private static final Set<String> SLOT_ICONS = Set.of("card", "component_bus", "container", "cpu", "eeprom", "floppy", "hdd", "memory", "rack_mountable", "tablet", "tool", "upgrade");
    private Button run;
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/" + path + ".png"); }
    public WorkstationScreen(WorkstationMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 176; imageHeight = menu.assembler ? 192 : 166; inventoryLabelY = menu.assembler ? 98 : 72; }
    @Override protected void init() {
        super.init();
        if (menu.assembler) run = addRenderableWidget(new Button(leftPos + 7, topPos + 89, 18, 18, Component.translatable("oc:gui.Assembler.Run"), button -> {
            if (minecraft != null && minecraft.gameMode != null && menu.canBuild()) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }, narration -> narration.get()) {
            @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
                RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1, 1, 1, alpha);
                graphics.blit(RUN, getX(), getY(), active ? 0 : 18, isMouseOver(mouseX, mouseY) ? 18 : 0, 18, 18, 36, 36);
                RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.disableBlend();
            }
        });
    }
    @Override protected void containerTick() { super.containerTick(); if (run != null) run.active = menu.canBuild(); }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.blit(menu.assembler ? ASSEMBLER : DISASSEMBLER, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int count = menu.assembler ? 22 : 1;
        for (int index = 0; index < count; index++) {
            var slot = menu.slots.get(index);
            AssemblerTemplates.Slot spec = index == 0 ? null : menu.slotInfo(index);
            if (spec != null && li.cil.oc.api.driver.item.Slot.None.equals(spec.kind) && !slot.hasItem()) continue;
            int x = leftPos + slot.x, y = topPos + slot.y;
            graphics.blit(SLOT, x - 1, y - 1, 0, 0, 18, 18, 18, 18);
            if (!slot.hasItem() && menu.assembler && !menu.busy()) {
                if (spec != null && spec.tier >= 0 && spec.tier <= 2) graphics.blit(texture("icons/tier" + spec.tier), x, y, 0, 0, 16, 16, 16, 16);
                // Original Textures.Icons has no icon for the template slot or custom slot kinds.
                if (spec != null && SLOT_ICONS.contains(spec.kind)) graphics.blit(texture("icons/" + spec.kind), x, y, 0, 0, 16, 16, 16, 16);
            }
        }
        int x = leftPos + (menu.assembler ? 28 : 18), y = topPos + (menu.assembler ? 92 : 65), width = menu.assembler ? 140 : 140;
        graphics.fill(x, y, x + width, y + 12, menu.assembler && !menu.busy() ? 0xffd5d5d5 : 0xff303030);
        graphics.fill(x + 1, y + 1, x + 1 + (int) ((width - 2) * Math.min(1, menu.progress() / 100)), y + 11, 0xff82a9c5);
        RenderSystem.disableBlend();
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.assembler) graphics.drawString(font, title, 8, 6, 0x404040, false);
        if (menu.assembler && !menu.busy()) {
            var validation = menu.validation();
            Component text = menu.inventory.getItem(0).isEmpty() ? Component.translatable("oc:gui.Assembler.InsertCase")
                    : validation != null && validation.progress() != null ? validation.progress() : Component.translatable("oc:gui.Assembler.Collect");
            graphics.drawString(font, font.plainSubstrByWidth(text.getString(), 136), 30, 94, 0x404040, false);
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        if (run != null) run.active = menu.canBuild();
        super.render(graphics, mouseX, mouseY, partial); renderTooltip(graphics, mouseX, mouseY);
        if (run != null && run.isMouseOver(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>(); lines.add(Component.translatable("oc:gui.Assembler.Run"));
            var validation = menu.validation(); if (validation != null && validation.valid()) lines.addAll(validation.warnings());
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (menu.busy() && mouseX >= leftPos + (menu.assembler ? 28 : 18) && mouseX < leftPos + (menu.assembler ? 168 : 158) && mouseY >= topPos + (menu.assembler ? 92 : 65) && mouseY < topPos + (menu.assembler ? 104 : 77)) {
            int seconds = menu.timeRemaining(); String time = String.format("%d:%02d", seconds / 60, seconds % 60);
            graphics.renderTooltip(font, Component.translatable("oc:gui.Assembler.Progress", (int) menu.progress(), time), mouseX, mouseY);
        }
    }
}
