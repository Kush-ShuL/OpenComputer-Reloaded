package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.Settings;
import li.cil.oc.client.renderer.ScreenRenderTypes;
import li.cil.oc.client.renderer.ScreenTextRenderer;
import li.cil.oc.common.entity.DroneEntity;
import li.cil.oc.common.menu.AgentMenu;
import li.cil.oc.common.network.AgentNetworking;
import li.cil.oc.server.agent.RobotController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** The original layered robot/drone panels, sockets, energy bar and embedded terminal. */
public final class AgentScreen extends AbstractContainerScreen<AgentMenu> {
    private static final ResourceLocation BAR = texture("gui/bar");
    private static final ResourceLocation BORDERS = texture("gui/borders");
    private static final ResourceLocation SELECTION = texture("gui/robot_selection");
    private static final ResourceLocation SCROLL = texture("gui/button_scroll");
    private static final ResourceLocation POWER = texture("gui/button_power");
    private static final Set<String> SLOT_ICONS = Set.of("tool", "card", "upgrade", "floppy", "hdd", "eeprom", "cpu", "memory", "component_bus", "container");
    private final Map<Integer, Character> pressed = new HashMap<>();
    private int lastCode;
    private PowerButton power;
    private boolean draggingScroll, draggingScreen;
    private float textX, textY, textScale;

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/" + path + ".png");
    }

    public AgentScreen(AgentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.robot ? 256 : 176;
        imageHeight = menu.robot ? 256 - menu.deltaY : 148;
        titleLabelY = inventoryLabelY = -10000;
    }

    private RobotController robot() { return menu.host instanceof RobotController robot ? robot : null; }
    private boolean running() { return menu.host instanceof DroneEntity drone ? drone.running() : robot() != null && robot().proxy.clientRunning; }
    private int inventoryX() { return menu.robot ? 169 : 97; }
    private int inventoryY() { return menu.robot ? 155 - menu.deltaY : 7; }
    private int powerX() { return leftPos + (menu.robot ? 26 : 28); }
    private int powerY() { return topPos + (menu.robot ? 156 - menu.deltaY : 48); }
    private int maxScrollRows() { return Math.max(0, (menu.host.mainInventory().getContainerSize() - 16 + 3) / 4); }
    private boolean canScroll() { return menu.robot && maxScrollRows() > 0; }

    private void send(AgentNetworking.Action action, int character, int code, double x, double y, int value, String text) {
        PacketDistributor.sendToServer(new AgentNetworking.Input(menu.containerId, action, character, code, x, y, value, text));
    }

    @Override protected void init() {
        super.init();
        draggingScroll = draggingScreen = false;
        power = addRenderableWidget(new PowerButton(leftPos + (menu.robot ? 5 : 7), topPos + (menu.robot ? 153 - menu.deltaY : 45)));
    }

    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        if (menu.robot && menu.scroll() / 4 > maxScrollRows()) scrollTo(0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.blit(texture("gui/" + (menu.robot ? menu.hasScreen ? "robot" : "robot_noscreen" : "drone")), leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        drawSlotIcons(graphics);
        drawEnergy(graphics);
        drawSelection(graphics);
        if (menu.robot) {
            drawScrollBar(graphics, mouseX, mouseY);
            drawRobotScreen(graphics);
        } else if (menu.host instanceof DroneEntity drone) {
            int y = topPos + 9;
            for (String line : drone.statusText().split("\n", -1)) {
                graphics.drawString(font, line, leftPos + 9, y, 0x66DD55, false);
                y += font.lineHeight;
            }
        }
        RenderSystem.disableBlend();
    }

    private void drawSlotIcons(GuiGraphics graphics) {
        if (menu.robot) for (int index = 0; index < 4; index++) {
            Slot slot = menu.slots.get(index);
            if (slot.hasItem()) continue;
            int x = leftPos + slot.x, y = topPos + slot.y, tier = menu.equipmentSlotTier(index);
            if (tier < 0) {
                graphics.blit(texture("icons/na"), x, y, 0, 0, 16, 16, 16, 16);
            } else {
                if (tier <= 2) graphics.blit(texture("icons/tier" + tier), x, y, 0, 0, 16, 16, 16, 16);
                String kind = menu.equipmentSlotType(index);
                if (SLOT_ICONS.contains(kind)) graphics.blit(texture("icons/" + kind), x, y, 0, 0, 16, 16, 16, 16);
            }
        }
        int first = menu.robot ? 4 : 0, count = menu.robot ? 16 : 8;
        for (int index = first; index < first + count; index++) {
            Slot slot = menu.slots.get(index);
            if (!slot.isActive()) graphics.blit(texture("icons/na"), leftPos + slot.x, topPos + slot.y, 0, 0, 16, 16, 16, 16);
        }
    }

    private void drawEnergy(GuiGraphics graphics) {
        int width = (int) (140 * Math.clamp(menu.energy() / (double) Math.max(1, menu.energyCapacity()), 0, 1));
        if (width > 0) graphics.blit(BAR, powerX(), powerY(), 0, 0, width, 12, 140, 12);
    }

    private void drawSelection(GuiGraphics graphics) {
        int selection = menu.host.selectedSlot() - (menu.robot ? menu.scroll() : 0);
        int count = menu.robot ? 16 : 8;
        if (selection < 0 || selection >= count || menu.host.selectedSlot() >= menu.host.mainInventory().getContainerSize()) return;
        int frame = (int) ((System.currentTimeMillis() % 1000) * 17 / 1000);
        int x = leftPos + inventoryX() - 1 + 18 * (selection % 4);
        int y = topPos + inventoryY() - 1 + 18 * (selection / 4);
        graphics.blit(SELECTION, x, y, 0, frame * 20, 20, 20, 20, 340);
    }

    private int scrollButtonY() {
        return topPos + inventoryY() + 1 + (canScroll() ? 79 * (menu.scroll() / 4) / maxScrollRows() : 0);
    }

    private void drawScrollBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + 244, y = scrollButtonY();
        boolean hover = canScroll() && (draggingScroll || inside(mouseX, mouseY, x, y, 6, 13));
        graphics.blit(SCROLL, x, y, 0, hover ? 13 : 0, 6, 13, 6, 26);
    }

    private void drawRobotScreen(GuiGraphics graphics) {
        textScale = 0;
        if (!menu.hasScreen) return;
        int width = Math.min(240, ScreenTextRenderer.characterWidth() * Settings.screenResolutionsByTier[0][0]);
        int height = Math.min(140, ScreenTextRenderer.characterHeight() * Settings.screenResolutionsByTier[0][1]);
        int x = leftPos + 8 + (240 - width) / 2, y = topPos + 8 + (140 - height) / 2;
        // Robot.scala uses the central six pixels of borders.png: a two-pixel
        // frame plus one pixel of inner margin, independent of GPU resolution.
        drawScreenBorder(graphics, x - 3, y - 3, width, height);
        graphics.flush();
        var buffer = robot() == null ? null : robot().clientScreen;
        if (buffer == null) return;
        int w = Math.max(1, buffer.renderWidth()), h = Math.max(1, buffer.renderHeight());
        textScale = Math.min(width / (float) w, height / (float) h);
        textX = x + (width - w * textScale) / 2;
        textY = y + (height - h * textScale) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0);
        graphics.pose().scale(textScale, textScale, 1);
        buffer.renderText(graphics.pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT);
        graphics.flush();
        graphics.pose().popPose();
    }

    private static void drawScreenBorder(GuiGraphics graphics, int x, int y, int width, int height) {
        float[] xs = {x, x + 2, x + width + 4, x + width + 6};
        float[] ys = {y, y + 2, y + height + 4, y + height + 6};
        float[] uv = {5 / 16F, 7 / 16F, 9 / 16F, 11 / 16F};
        var vertices = graphics.bufferSource().getBuffer(ScreenRenderTypes.texture(BORDERS, false));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            float u0 = col == 1 ? 7.25F / 16 : uv[col], u1 = col == 1 ? 8.75F / 16 : uv[col + 1];
            float v0 = row == 1 ? 7.25F / 16 : uv[row], v1 = row == 1 ? 8.75F / 16 : uv[row + 1];
            vertices.addVertex(graphics.pose().last(), xs[col], ys[row + 1], 0).setColor(-1).setUv(u0, v1).setLight(LightTexture.FULL_BRIGHT);
            vertices.addVertex(graphics.pose().last(), xs[col + 1], ys[row + 1], 0).setColor(-1).setUv(u1, v1).setLight(LightTexture.FULL_BRIGHT);
            vertices.addVertex(graphics.pose().last(), xs[col + 1], ys[row], 0).setColor(-1).setUv(u1, v0).setLight(LightTexture.FULL_BRIGHT);
            vertices.addVertex(graphics.pose().last(), xs[col], ys[row], 0).setColor(-1).setUv(u0, v0).setLight(LightTexture.FULL_BRIGHT);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.robot || hoveredSlot == null || !menu.getCarried().isEmpty()) return;
        boolean hoveringEquipment = hoveredSlot.index > 0 && hoveredSlot.index < 4;
        boolean hoveringPlayer = hoveredSlot.index >= 20;
        if (!hoveringEquipment && !hoveringPlayer) return;
        for (Slot slot : menu.slots) {
            boolean equipment = slot.index > 0 && slot.index < 4;
            boolean player = slot.index >= 20;
            if (!slot.isActive() || !(hoveringEquipment && player || hoveringPlayer && equipment)) continue;
            boolean valid = equipment ? hoveredSlot.hasItem() && slot.mayPlace(hoveredSlot.getItem()) : slot.hasItem() && hoveredSlot.mayPlace(slot.getItem());
            if (valid) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80FFFFFF);
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
        renderTooltip(graphics, mouseX, mouseY);
        if (power.isMouseOver(mouseX, mouseY)) {
            String label = Component.translatable(running() ? "oc:gui.Robot.TurnOff" : "oc:gui.Robot.TurnOn").getString();
            graphics.renderComponentTooltip(font, Arrays.stream(label.split("\\[nl\\]")).<Component>map(Component::literal).toList(), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, powerX(), powerY(), 140, 12)) {
            int energy = menu.energy(), maximum = menu.energyCapacity();
            graphics.renderTooltip(font, Component.translatable("oc:gui.Robot.Power").append(": " + (int) (energy * 100.0 / Math.max(1, maximum)) + "% (" + energy + "/" + maximum + ")"), mouseX, mouseY);
        }
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {}
    private boolean keyboard() { return robot() != null && robot().clientScreen != null && robot().clientHasKeyboard; }
    @Override public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_F11 || !keyboard()) return super.keyPressed(key, scancode, modifiers);
        if (Screen.isPaste(key)) {
            String text = li.cil.oc.client.ClipboardInput.read();
            if (text == null) return true;
            for (int start = 0; start < text.length();) {
                int end = Math.min(start + 8192, text.length());
                if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
                send(AgentNetworking.Action.CLIPBOARD, 0, 0, 0, 0, 0, text.substring(start, end));
                start = end;
            }
            return true;
        }
        lastCode = LegacyKeyCodes.of(key);
        if (!LegacyKeyCodes.printable(key) || Screen.hasControlDown() || Screen.hasAltDown()) {
            char character = Screen.hasControlDown() && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z ? (char) (key - GLFW.GLFW_KEY_A + 1) : LegacyKeyCodes.controlCharacter(key);
            pressed.put(lastCode, character);
            send(AgentNetworking.Action.KEY_DOWN, character, lastCode, 0, 0, 0, "");
        }
        return true;
    }

    @Override public boolean charTyped(char character, int modifiers) {
        if (!keyboard()) return super.charTyped(character, modifiers);
        pressed.put(lastCode, character);
        send(AgentNetworking.Action.KEY_DOWN, character, lastCode, 0, 0, 0, "");
        return true;
    }

    @Override public boolean keyReleased(int key, int scancode, int modifiers) {
        Character character = pressed.remove(LegacyKeyCodes.of(key));
        if (character != null) {
            send(AgentNetworking.Action.KEY_UP, character, LegacyKeyCodes.of(key), 0, 0, 0, "");
            return true;
        }
        return super.keyReleased(key, scancode, modifiers);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private boolean overScrollBar(double x, double y) { return menu.robot && inside(x, y, leftPos + 244, topPos + inventoryY(), 7, 94); }
    private void scrollTo(int row) {
        row = Math.max(0, Math.min(maxScrollRows(), row));
        if (row == menu.scroll() / 4) return;
        menu.scroll(row);
        send(AgentNetworking.Action.SCROLL, 0, 0, 0, 0, row, "");
    }
    private void scrollMouse(double y) {
        scrollTo((int) Math.round((y - topPos - inventoryY() + 1 - 6.5) * maxScrollRows() / 81.0));
    }

    private double[] coordinates(double x, double y) {
        if (robot() == null || robot().clientScreen == null || textScale <= 0) return null;
        double column = (x - textX) / textScale / ScreenTextRenderer.characterWidth(), row = (y - textY) / textScale / ScreenTextRenderer.characterHeight();
        return column >= 0 && row >= 0 && column < robot().clientScreen.getViewportWidth() && row < robot().clientScreen.getViewportHeight() ? new double[]{column, row} : null;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && canScroll() && overScrollBar(x, y)) {
            draggingScroll = true;
            scrollMouse(y);
            return true;
        }
        double[] point = coordinates(x, y);
        if (point != null && button >= 0 && button < 2) {
            draggingScreen = true;
            send(AgentNetworking.Action.TOUCH, 0, 0, point[0], point[1], button, "");
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingScroll && button == 0) { scrollMouse(y); return true; }
        double[] point = coordinates(x, y);
        if (draggingScreen && point != null && button >= 0 && button < 2) {
            send(AgentNetworking.Action.DRAG, 0, 0, point[0], point[1], button, "");
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (draggingScroll && button == 0) { draggingScroll = false; return true; }
        double[] point = coordinates(x, y);
        if (draggingScreen && button >= 0 && button < 2) {
            draggingScreen = false;
            if (point != null) send(AgentNetworking.Action.DROP, 0, 0, point[0], point[1], button, "");
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (dy != 0 && canScroll() && (inside(x, y, leftPos + inventoryX(), topPos + inventoryY(), 72, 72) || overScrollBar(x, y))) {
            scrollTo(menu.scroll() / 4 + (dy > 0 ? -1 : 1));
            return true;
        }
        double[] point = coordinates(x, y);
        if (point != null && dy != 0) {
            send(AgentNetworking.Action.MOUSE_SCROLL, 0, 0, point[0], point[1], dy > 0 ? 1 : -1, "");
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override public void removed() {
        for (var key : pressed.entrySet()) send(AgentNetworking.Action.KEY_UP, key.getValue(), key.getKey(), 0, 0, 0, "");
        pressed.clear();
        super.removed();
    }

    private final class PowerButton extends Button {
        PowerButton(int x, int y) {
            super(x, y, 18, 18, Component.translatable("oc:gui.Robot.TurnOn"), button -> send(AgentNetworking.Action.POWER, 0, 0, 0, 0, 0, ""), DEFAULT_NARRATION);
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            setMessage(Component.translatable(running() ? "oc:gui.Robot.TurnOff" : "oc:gui.Robot.TurnOn"));
            graphics.blit(POWER, getX(), getY(), running() ? 18 : 0, isMouseOver(mouseX, mouseY) ? 18 : 0, 18, 18, 36, 36);
            RenderSystem.disableBlend();
        }
    }
}
