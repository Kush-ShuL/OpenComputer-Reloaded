package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.common.block.entity.AdapterBlockEntity;
import li.cil.oc.common.init.Adapters;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Original six open-side overlays with their original vertex and UV mapping. */
@EventBusSubscriber(modid = "opencomputers", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AdapterBlockEntityRenderer implements BlockEntityRenderer<AdapterBlockEntity> {
    private static final ResourceLocation ON = ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/blocks/overlay/adapter_on.png");
    public AdapterBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(Adapters.TYPE.get(), AdapterBlockEntityRenderer::new); }
    @Override public void render(AdapterBlockEntity adapter, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (adapter.sideMask() == 0) return;
        pose.pushPose(); pose.translate(.5, .5, .5); pose.scale(1.0025f, -1.0025f, 1.0025f); pose.translate(-.5, -.5, -.5);
        var out = buffers.getBuffer(RenderType.entityTranslucentEmissive(ON));
        for (Direction side : Direction.values()) {
            if (!adapter.isSideOpen(side)) continue;
            float[][] vertices = switch (side) {
                case DOWN -> new float[][]{{0,1,0,1,0},{1,1,0,0,0},{1,1,1,0,1},{0,1,1,1,1}};
                case UP -> new float[][]{{0,0,0,1,1},{0,0,1,1,0},{1,0,1,0,0},{1,0,0,0,1}};
                case NORTH -> new float[][]{{1,1,0,0,1},{0,1,0,1,1},{0,0,0,1,0},{1,0,0,0,0}};
                case SOUTH -> new float[][]{{0,1,1,0,1},{1,1,1,1,1},{1,0,1,1,0},{0,0,1,0,0}};
                case WEST -> new float[][]{{0,1,0,0,1},{0,1,1,1,1},{0,0,1,1,0},{0,0,0,0,0}};
                case EAST -> new float[][]{{1,1,1,0,1},{1,1,0,1,1},{1,0,0,1,0},{1,0,1,0,0}};
            };
            for (float[] vertex : vertices) out.addVertex(pose.last(), vertex[0], vertex[1], vertex[2]).setColor(-1).setUv(vertex[3], vertex[4]).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), side.getStepX(), -side.getStepY(), side.getStepZ());
        }
        pose.popPose();
    }
}
