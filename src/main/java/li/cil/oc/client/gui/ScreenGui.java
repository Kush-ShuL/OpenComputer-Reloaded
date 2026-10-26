package li.cil.oc.client.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import li.cil.oc.common.block.entity.ScreenBlockEntity;
import li.cil.oc.common.network.ScreenNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Original bordered, non-pausing terminal with Unicode input and pixel-precise mouse coordinates. */
public final class ScreenGui extends Screen {
    private static final ResourceLocation BORDER = ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/gui/borders.png");
    private static final ResourceLocation MISSING_KEYBOARD = ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/gui/keyboard_missing.png");
    private final ScreenBlockEntity entity;
    private final UUID session;
    private final Map<Integer, Character> pressed = new HashMap<>();
    private int lastKey, lastCode;
    private float scale = 1;
    private float left, top;
    private int textWidth, textHeight;
    private boolean mousePressed;
    private long missingKeyboardUntil;
    public ScreenGui(ScreenBlockEntity entity, UUID session) { super(Component.translatableWithFallback("gui.opencomputers.screen", "Screen")); this.entity = entity; this.session = session; }
    public BlockPos position() { return entity.getBlockPos(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { if (minecraft == null || minecraft.level == null || entity.isRemoved() || !entity.isOrigin()) onClose(); }
    private void updateLayout() {
        textWidth = Math.max(1, entity.buffer().renderWidth());
        textHeight = Math.max(1, entity.buffer().renderHeight());
        float margin = Math.min(16F, Math.min(width, height) / 4F);
        scale = Math.min(Math.max(1F, width - margin * 2) / (textWidth + 16F),
                Math.max(1F, height - margin * 2) / (textHeight + 16F));
        left = (width - (textWidth + 16F) * scale) / 2F;
        top = (height - (textHeight + 16F) * scale) / 2F;
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        updateLayout();
        graphics.pose().pushPose(); graphics.pose().translate(left, top, 0); graphics.pose().scale(scale, scale, 1);
        drawBorder(graphics);
        graphics.pose().pushPose(); graphics.pose().translate(8, 8, 0);
        if (entity.buffer().isRenderingEnabled()) entity.buffer().renderText(graphics.pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT);
        graphics.pose().popPose(); graphics.flush();
        if (System.currentTimeMillis() < missingKeyboardUntil) graphics.blit(MISSING_KEYBOARD, 8 + textWidth - 16, 8 + textHeight - 16, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
    }
    // Legacy OC draws its border directly over the world; no menu backdrop or post blur.
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    private void drawBorder(GuiGraphics graphics) {
        float[] x = {0, 7, 9 + textWidth, 16 + textWidth};
        float[] y = {0, 7, 9 + textHeight, 16 + textHeight};
        float[] uv = {0, 7F / 16F, 9F / 16F, 1};
        VertexConsumer out = graphics.bufferSource().getBuffer(li.cil.oc.client.renderer.ScreenRenderTypes.texture(BORDER, false));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            float u0 = col == 1 ? 7.25F / 16F : uv[col];
            float u1 = col == 1 ? 8.75F / 16F : uv[col + 1];
            float v0 = row == 1 ? 7.25F / 16F : uv[row];
            float v1 = row == 1 ? 8.75F / 16F : uv[row + 1];
            out.addVertex(graphics.pose().last(), x[col], y[row + 1], 0).setColor(-1).setUv(u0, v1).setLight(LightTexture.FULL_BRIGHT);
            out.addVertex(graphics.pose().last(), x[col + 1], y[row + 1], 0).setColor(-1).setUv(u1, v1).setLight(LightTexture.FULL_BRIGHT);
            out.addVertex(graphics.pose().last(), x[col + 1], y[row], 0).setColor(-1).setUv(u1, v0).setLight(LightTexture.FULL_BRIGHT);
            out.addVertex(graphics.pose().last(), x[col], y[row], 0).setColor(-1).setUv(u0, v0).setLight(LightTexture.FULL_BRIGHT);
        }
    }
    private boolean keyboard() { if (entity.clientHasKeyboard) return true; missingKeyboardUntil = System.currentTimeMillis() + 1000; return false; }
    public void send(ScreenNetworking.InputType action, int character, int code, double x, double y, int value, String text) {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(new ScreenNetworking.Input(position(), session, action, character, code, x, y, value, text));
    }
    @Override public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_F11) return super.keyPressed(key, scancode, modifiers);
        if (!keyboard()) return true;
        if (Screen.isPaste(key)) { paste(); return true; }
        lastKey = key; lastCode = LegacyKeyCodes.of(key);
        if (!LegacyKeyCodes.printable(key) || Screen.hasControlDown() || Screen.hasAltDown()) {
            char character = Screen.hasControlDown() && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z ? (char) (key - GLFW.GLFW_KEY_A + 1) : LegacyKeyCodes.controlCharacter(key);
            pressed.put(lastCode, character); send(ScreenNetworking.InputType.KEY_DOWN, character, lastCode, 0, 0, 0, "");
        }
        return true;
    }
    @Override public boolean charTyped(char character, int modifiers) {
        if (!keyboard()) return true;
        pressed.put(lastCode, character); send(ScreenNetworking.InputType.KEY_DOWN, character, lastCode, 0, 0, 0, ""); return true;
    }
    @Override public boolean keyReleased(int key, int scancode, int modifiers) {
        int code = LegacyKeyCodes.of(key); Character character = pressed.remove(code);
        if (character != null) send(ScreenNetworking.InputType.KEY_UP, character, code, 0, 0, 0, "");
        return true;
    }
    private void paste() {
        String text = li.cil.oc.client.ClipboardInput.read();
        if (text == null) return;
        for (int start = 0; start < text.length();) {
            int end = Math.min(start + 8192, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            send(ScreenNetworking.InputType.CLIPBOARD, 0, 0, 0, 0, 0, text.substring(start, end)); start = end;
        }
    }
    private double[] coordinates(double x, double y) {
        updateLayout();
        double bx = ((x - left) / scale - 8) / li.cil.oc.client.renderer.ScreenTextRenderer.characterWidth();
        double by = ((y - top) / scale - 8) / li.cil.oc.client.renderer.ScreenTextRenderer.characterHeight();
        return bx >= 0 && by >= 0 && bx < entity.buffer().getViewportWidth() && by < entity.buffer().getViewportHeight() ? new double[]{bx, by} : null;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 2) { if (keyboard()) paste(); return true; }
        double[] point = coordinates(x, y);
        if (point != null && button >= 0 && button < 2) { mousePressed = true; send(ScreenNetworking.InputType.TOUCH, 0, 0, point[0], point[1], button, ""); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        double[] point = coordinates(x, y);
        if (mousePressed && point != null) { send(ScreenNetworking.InputType.DRAG, 0, 0, point[0], point[1], button, ""); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        double[] point = coordinates(x, y);
        if (mousePressed) {
            if (point != null) send(ScreenNetworking.InputType.DROP, 0, 0, point[0], point[1], button, "");
            mousePressed = false; return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        double[] point = coordinates(x, y);
        if (point != null && dy != 0) { send(ScreenNetworking.InputType.SCROLL, 0, 0, point[0], point[1], dy > 0 ? 1 : -1, ""); return true; }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override public void removed() {
        for (var entry : pressed.entrySet()) send(ScreenNetworking.InputType.KEY_UP, entry.getValue(), entry.getKey(), 0, 0, 0, "");
        pressed.clear(); send(ScreenNetworking.InputType.CLOSE, 0, 0, 0, 0, 0, ""); super.removed();
    }
}
