package li.cil.oc.client.gui;

import li.cil.oc.common.item.ComponentItem;
import li.cil.oc.common.item.data.DriveData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DriveConfigurationScreen extends Screen {
    private final InteractionHand hand;
    private final DriveData data;
    private boolean unmanaged;
    public DriveConfigurationScreen(Player player, InteractionHand hand) {
        super(Component.translatableWithFallback("gui.opencomputers.drive.configuration", "Drive configuration"));
        this.hand = hand; data = new DriveData(player.getItemInHand(hand)); unmanaged = data.isUnmanaged;
    }
    @Override public boolean isPauseScreen() { return false; }
    private Component mode() { return Component.translatableWithFallback(unmanaged ? "gui.opencomputers.drive.raw" : "gui.opencomputers.drive.managed", unmanaged ? "Mode: raw sectors" : "Mode: managed filesystem"); }
    @Override protected void init() {
        int x = width / 2 - 110, y = height / 2 - 15;
        Button mode = addRenderableWidget(Button.builder(mode(), button -> { unmanaged = !unmanaged; button.setMessage(mode()); }).bounds(x, y, 220, 20).build());
        mode.active = !data.isLocked();
        Button lock = addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.opencomputers.drive.lock", "Lock to your name"), button -> {
            PacketDistributor.sendToServer(new ComponentItem.DriveSettingsPayload(hand, unmanaged, true)); onClose();
        }).bounds(x, y + 25, 220, 20).build()); lock.active = !data.isLocked();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
            if (!data.isLocked()) PacketDistributor.sendToServer(new ComponentItem.DriveSettingsPayload(hand, unmanaged, false)); onClose();
        }).bounds(x, y + 50, 220, 20).build());
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        graphics.fill(width / 2 - 126, height / 2 - 55, width / 2 + 126, height / 2 + 90, 0xEF242424);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 43, 0xFFFFFF);
        if (data.isLocked()) graphics.drawCenteredString(font, Component.literal(data.lockInfo), width / 2, height / 2 + 78, 0xFFCC66);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
