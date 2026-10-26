package li.cil.oc.client;

import li.cil.oc.common.block.entity.ScreenBlockEntity;
import li.cil.oc.common.item.AnalyzerItem;
import li.cil.oc.common.network.ToolNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only font-aware screen selection and Ctrl+analyze clipboard behavior. */
public final class AnalyzerClient {
    private AnalyzerClient() {}
    public static void initialize() { AnalyzerItem.setClientActions(AnalyzerClient::copyScreenLine); }
    public static void receive(ToolNetworking.Analyze packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && Screen.hasControlDown()) {
            client.keyboardHandler.setClipboard(packet.address());
            client.player.displayClientMessage(Component.translatable("oc:gui.Analyzer.AddressCopied"), false);
        }
    }
    private static boolean copyScreenLine(ScreenBlockEntity clicked, Vec3 point, InteractionHand hand) {
        ScreenBlockEntity origin = clicked.origin();
        Vec3 relative = point.subtract(Vec3.atCenterOf(origin.getBlockPos()));
        double x = relative.dot(Vec3.atLowerCornerOf(origin.right().getNormal())) + .5;
        double y = origin.groupHeight() - .5 - relative.dot(Vec3.atLowerCornerOf(origin.up().getNormal()));
        double border = 2.25 / 16;
        if (x <= border || y <= border || x >= origin.groupWidth() - border || y >= origin.groupHeight() - border) return false;
        var buffer = origin.buffer();
        double scale = Math.min((origin.groupWidth() - 2 * border) / buffer.renderWidth(), (origin.groupHeight() - 2 * border) / buffer.renderHeight());
        double top = (origin.groupHeight() - buffer.renderHeight() * scale) / 2;
        int line = (int) Math.floor((y - top) / (buffer.renderHeight() * scale) * buffer.getViewportHeight());
        PacketDistributor.sendToServer(new ToolNetworking.CopyScreenLine(clicked.getBlockPos(), hand, line));
        return true;
    }
}
