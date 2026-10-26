package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import li.cil.oc.common.block.entity.DiskDriveBlockEntity;
import li.cil.oc.common.init.DiskDrives;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The original partially inserted disk and white, full-bright access indicator. */
@EventBusSubscriber(modid = "opencomputers", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DiskDriveBlockEntityRenderer implements BlockEntityRenderer<DiskDriveBlockEntity> {
    private static final ResourceLocation ACTIVITY = ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/blocks/overlay/diskdrive_front_activity.png");
    private final ItemRenderer items;
    public DiskDriveBlockEntityRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(DiskDrives.TYPE.get(), DiskDriveBlockEntityRenderer::new); }
    @Override public void render(DiskDriveBlockEntity drive, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose(); pose.translate(.5, .5, .5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (drive.facing()) { case WEST -> -90; case NORTH -> 180; case EAST -> 90; default -> 0; }));
        if (!drive.isEmpty()) {
            pose.pushPose(); pose.translate(0, 3.5 / 16, 6.0 / 16); pose.mulPose(Axis.XN.rotationDegrees(90)); pose.scale(.5f, .5f, .5f);
            int diskLight = drive.getLevel() == null ? light : net.minecraft.client.renderer.LevelRenderer.getLightColor(drive.getLevel(), drive.getBlockPos().relative(drive.facing()));
            items.renderStatic(drive.getItem(0), ItemDisplayContext.FIXED, diskLight, overlay, pose, buffers, drive.getLevel(), 0);
            pose.popPose();
        }
        if (System.currentTimeMillis() - drive.lastAccess < 400 && drive.getLevel() != null && drive.getLevel().random.nextDouble() > .1) {
            pose.translate(-.5, .5, .505); pose.scale(1, -1, 1);
            var vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(ACTIVITY));
            vertices.addVertex(pose.last(), 0, 1, 0).setColor(-1).setUv(0, 1).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, 1);
            vertices.addVertex(pose.last(), 1, 1, 0).setColor(-1).setUv(1, 1).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, 1);
            vertices.addVertex(pose.last(), 1, 0, 0).setColor(-1).setUv(1, 0).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, 1);
            vertices.addVertex(pose.last(), 0, 0, 0).setColor(-1).setUv(0, 0).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, 1);
        }
        pose.popPose();
    }
}
