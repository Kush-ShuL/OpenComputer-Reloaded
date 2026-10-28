package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.BooleanSupplier;
import li.cil.oc.common.item.ComponentItem;
import li.cil.oc.common.item.data.DriveData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** The original two-mode drive window, read-only lock and destructive-mode warnings. */
public final class DriveConfigurationScreen extends Screen {
    private static final ResourceLocation PANEL = texture("drive");
    private static final ResourceLocation MODE = texture("button_drive_mode");
    private static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 120;
    private final Player player;
    private final InteractionHand hand;
    private DriveData data;
    private Button lock;
    private int left, top;

    public DriveConfigurationScreen(Player player, InteractionHand hand) {
        super(player.getItemInHand(hand).getHoverName());
        this.player = player; this.hand = hand; data = new DriveData(stack());
    }
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/gui/" + name + ".png"); }
    private ItemStack stack() { return player.getItemInHand(hand); }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() {
        left = (width - PANEL_WIDTH) / 2; top = (height - PANEL_HEIGHT) / 2;
        addRenderableWidget(new ModeButton(left + 11, top + 11, 74, Component.translatable("oc:gui.Drive.Managed"), () -> !data.isUnmanaged, () -> mode(false)));
        addRenderableWidget(new ModeButton(left + 91, top + 11, 74, Component.translatable("oc:gui.Drive.Unmanaged"), () -> data.isUnmanaged, () -> mode(true)));
        lock = addRenderableWidget(new ModeButton(left + 11, top + PANEL_HEIGHT - 42, 44, Component.translatable("oc:gui.Drive.ReadOnlyLock"), () -> data.isLocked(), this::lock));
        update();
    }
    private void update() { data = new DriveData(stack()); lock.active = !data.isLocked(); }
    private void mode(boolean unmanaged) {
        PacketDistributor.sendToServer(new ComponentItem.DriveSettingsPayload(hand, unmanaged, false));
        DriveData.setUnmanaged(stack(), unmanaged); update();
    }
    private void lock() {
        PacketDistributor.sendToServer(new ComponentItem.DriveSettingsPayload(hand, data.isUnmanaged, true));
        DriveData.lock(stack(), player); update();
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        graphics.blit(PANEL, left, top, PANEL_WIDTH, PANEL_HEIGHT, 0, 0, 176, 111, 176, 111);
        RenderSystem.disableBlend();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawWordWrap(font, Component.translatable("oc:gui.Drive.Warning"), left + 11, top + 37, PANEL_WIDTH - 20, 0x404040);
        graphics.drawWordWrap(font, Component.translatable("oc:gui.Drive.ReadOnlyLockWarning"), left + 61, top + PANEL_HEIGHT - 48, PANEL_WIDTH - 68, 0x404040);
    }
    private final class ModeButton extends Button {
        private final BooleanSupplier selected;
        ModeButton(int x, int y, int width, Component text, BooleanSupplier selected, Runnable action) {
            super(x, y, width, 18, text, button -> action.run(), DEFAULT_NARRATION); this.selected = selected;
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            RenderSystem.enableBlend();
            graphics.blit(MODE, getX(), getY(), getWidth(), getHeight(), selected.getAsBoolean() ? 74 : 0, isHovered() && active ? 18 : 0, 74, 18, 148, 36);
            RenderSystem.disableBlend();
            graphics.drawCenteredString(font, getMessage(), getX() + getWidth() / 2, getY() + 5, !active ? 0xA0A0A0 : isHovered() ? 0xFFFFA0 : 0x608060);
        }
    }
}
