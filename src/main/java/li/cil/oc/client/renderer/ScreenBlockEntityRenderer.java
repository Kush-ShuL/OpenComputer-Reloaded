package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.Settings;
import li.cil.oc.common.block.ScreenBlock;
import li.cil.oc.common.block.entity.ScreenBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** The shell is a baked model; only the original emissive text and orientation overlay are dynamic. */
public final class ScreenBlockEntityRenderer implements BlockEntityRenderer<ScreenBlockEntity> {
    private static final ResourceLocation UP_INDICATOR = ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/blocks/overlay/screen_up_indicator.png");
    public ScreenBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ScreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!screen.isOrigin()) return;
        var client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera().getPosition();
        var right = screen.right(); var up = screen.up(); var front = screen.facing();
        if (camera.subtract(Vec3.atCenterOf(screen.getBlockPos())).dot(Vec3.atLowerCornerOf(front.getNormal())) <= 0) return;
        double distance = playerDistanceSquared(screen.screenBounds(), client.player == null ? camera : client.player.position()) / Math.min(screen.groupWidth(), screen.groupHeight());
        double maximum = Settings.get().maxScreenTextRenderDistance * Settings.get().maxScreenTextRenderDistance;
        if (distance > maximum) return;
        float tx = 0.5F + front.getStepX() * 0.5F - right.getStepX() * 0.5F + up.getStepX() * (screen.groupHeight() - 0.5F);
        float ty = 0.5F + front.getStepY() * 0.5F - right.getStepY() * 0.5F + up.getStepY() * (screen.groupHeight() - 0.5F);
        float tz = 0.5F + front.getStepZ() * 0.5F - right.getStepZ() * 0.5F + up.getStepZ() * (screen.groupHeight() - 0.5F);
        Matrix4f orientation = new Matrix4f().m00(right.getStepX()).m01(right.getStepY()).m02(right.getStepZ())
                .m10(-up.getStepX()).m11(-up.getStepY()).m12(-up.getStepZ())
                .m20(front.getStepX()).m21(front.getStepY()).m22(front.getStepZ()).m30(tx).m31(ty).m32(tz);
        pose.pushPose(); pose.mulPose(orientation);
        drawUpIndicator(screen, pose, buffers);
        float availableW = screen.groupWidth() - 4.5F / 16F, availableH = screen.groupHeight() - 4.5F / 16F;
        float width = screen.buffer().renderWidth(), height = screen.buffer().renderHeight();
        float scale = Math.min(availableW / width, availableH / height);
        pose.translate((screen.groupWidth() - width * scale) / 2F, (screen.groupHeight() - height * scale) / 2F, 0);
        pose.scale(scale, scale, 1);
        pose.translate(0, 0, 0.01F);
        double fadeStart = Settings.get().screenTextFadeStartDistance * Settings.get().screenTextFadeStartDistance;
        float alpha = distance <= fadeStart ? 1 : (float) Math.clamp((maximum - distance) / Math.max(0.0001, maximum - fadeStart), 0, 1);
        if (screen.buffer().isRenderingEnabled()) ScreenTextRenderer.draw(screen.buffer(), pose, buffers, LightTexture.FULL_BRIGHT, alpha, false);
        pose.popPose();
    }
    private static void drawUpIndicator(ScreenBlockEntity screen, PoseStack pose, MultiBufferSource buffers) {
        var player = Minecraft.getInstance().player;
        if (!screen.facing().getAxis().isVertical() || player == null) return;
        var stack = player.getMainHandItem();
        boolean screenItem = stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ScreenBlock;
        boolean wrench = li.cil.oc.common.integration.WrenchTools.holdsApplicableWrench(player, screen.getBlockPos());
        if (!screenItem && !wrench) return;
        float x = screen.groupWidth() / 2F - 0.5F, y = screen.groupHeight() / 2F - 0.5F;
        var out = buffers.getBuffer(ScreenRenderTypes.texture(UP_INDICATOR, false));
        out.addVertex(pose.last(), x, y + 1, 0.05F).setColor(-1).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(pose.last(), x + 1, y + 1, 0.05F).setColor(-1).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(pose.last(), x + 1, y, 0.05F).setColor(-1).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(pose.last(), x, y, 0.05F).setColor(-1).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
    }
    // Upstream expands its distance falloff by the dimensions of a joined screen.
    private static double playerDistanceSquared(AABB bounds, Vec3 player) {
        double x = Math.max(0, Math.abs(player.x - (bounds.minX + bounds.maxX) / 2) - bounds.getXsize());
        double y = Math.max(0, Math.abs(player.y - (bounds.minY + bounds.maxY) / 2) - bounds.getYsize());
        double z = Math.max(0, Math.abs(player.z - (bounds.minZ + bounds.maxZ) / 2) - bounds.getZsize());
        return x * x + y * y + z * z;
    }
    @Override public AABB getRenderBoundingBox(ScreenBlockEntity screen) { return screen.screenBounds(); }
    @Override public int getViewDistance() {
        return (int) Math.ceil(Settings.get().maxScreenTextRenderDistance * Math.sqrt(Math.min(Settings.get().maxScreenWidth, Settings.get().maxScreenHeight)) + Math.max(Settings.get().maxScreenWidth, Settings.get().maxScreenHeight));
    }
}
