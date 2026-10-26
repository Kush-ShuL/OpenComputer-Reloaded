package li.cil.oc.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.common.block.entity.HologramBlockEntity;
import li.cil.oc.server.component.Hologram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Exposed voxel surfaces, cached on the GPU and drawn with the upstream two-pass projection. */
public final class HologramBlockEntityRenderer implements BlockEntityRenderer<HologramBlockEntity> {
    private static final Map<HologramBlockEntity, CachedMesh> CACHE = new IdentityHashMap<>();
    private static final long CACHE_TIMEOUT = 5_000_000_000L;
    private static final Random RANDOM = new Random();
    private record Face(int dx, int dy, int dz, float[][] vertices) {}
    // Positions, winding, and UVs are the original HologramRenderer's six quads.
    private static final Face[] FACES = {
            new Face(0,0,1,new float[][]{{1,1,1,0,0},{0,1,1,1,0},{0,0,1,1,1},{1,0,1,0,1}}),
            new Face(0,0,-1,new float[][]{{1,0,0,0,0},{0,0,0,1,0},{0,1,0,1,1},{1,1,0,0,1}}),
            new Face(1,0,0,new float[][]{{1,1,1,1,0},{1,0,1,1,1},{1,0,0,0,1},{1,1,0,0,0}}),
            new Face(-1,0,0,new float[][]{{0,0,1,1,0},{0,1,1,1,1},{0,1,0,0,1},{0,0,0,0,0}}),
            new Face(0,1,0,new float[][]{{1,1,0,0,0},{0,1,0,1,0},{0,1,1,1,1},{1,1,1,0,1}}),
            new Face(0,-1,0,new float[][]{{1,0,1,0,0},{0,0,1,1,0},{0,0,0,1,1},{1,0,0,0,1}})
    };
    private static final class CachedMesh {
        VertexBuffer buffer;
        int[] volume, palette;
        long revision = Long.MIN_VALUE, accessed;
        boolean empty = true, failed;
        void close() { if (buffer != null) { buffer.close(); buffer = null; } }
    }
    public HologramBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(HologramBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Hologram hologram = entity.hologram();
        if (!hologram.hasPower || entity.getLevel() == null || HologramRenderTypes.shader() == null) return;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance = camera.distanceToSqr(Vec3.atCenterOf(entity.getBlockPos()));
        double maximum = entity.renderDistanceSquared(false), start = Math.min(maximum, entity.renderDistanceSquared(true));
        if (distance >= maximum || maximum <= 0) return;
        float alpha = .75F * (float) (distance <= start ? 1 : Math.max(0, 1 - (distance - start) / Math.max(1e-9, maximum - start)));
        CachedMesh mesh = CACHE.computeIfAbsent(entity, ignored -> new CachedMesh());
        mesh.accessed = System.nanoTime();
        refresh(mesh, hologram);
        if (mesh.failed) { renderFailure(pose, buffers); return; }
        if (mesh.empty || mesh.buffer == null) return;

        float[] oldColor = RenderSystem.getShaderColor().clone();
        pose.pushPose();
        try {
            pose.translate(.5, .5, .5);
            switch (entity.yaw()) {
                case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
                case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
                case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90));
                default -> { }
            }
            switch (entity.pitch()) {
                case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90));
                case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90));
                default -> { }
            }
            rotate(pose, hologram.rotationAngle, hologram.rotationX, hologram.rotationY, hologram.rotationZ);
            float rotation = hologram.rotationSpeed * (entity.getLevel().getGameTime() % (360 * 20 - 1) + partialTick) / 20F;
            rotate(pose, rotation, hologram.rotationSpeedX, hologram.rotationSpeedY, hologram.rotationSpeedZ);
            pose.scale(1.001F, 1.001F, 1.001F);
            pose.translate((hologram.translation.x * 3 - 1.5) * hologram.scale,
                    hologram.translation.y * 2 * hologram.scale, (hologram.translation.z * 3 - 1.5) * hologram.scale);
            if (Settings.get().hologramFlickerFrequency > 0 && RANDOM.nextDouble() < Settings.get().hologramFlickerFrequency) {
                pose.scale((float) (1 + RANDOM.nextGaussian() * .01), (float) (1 + RANDOM.nextGaussian() * .001), (float) (1 + RANDOM.nextGaussian() * .01));
                pose.translate(RANDOM.nextGaussian() * .01, RANDOM.nextGaussian() * .01, RANDOM.nextGaussian() * .01);
            }
            float scale = (float) (hologram.scale / 16);
            pose.scale(scale, scale, scale);
            Vector3f localCamera = new Matrix4f(pose.last().pose()).invert().transformPosition(new Vector3f());
            boolean inside = localCamera.x >= 0 && localCamera.x <= Hologram.WIDTH && localCamera.y >= 0 && localCamera.y <= Hologram.HEIGHT
                    && localCamera.z >= 0 && localCamera.z <= Hologram.WIDTH;
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            draw(mesh, HologramRenderTypes.depth(inside), modelView);
            draw(mesh, HologramRenderTypes.color(inside), modelView);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(oldColor[0], oldColor[1], oldColor[2], oldColor[3]);
            pose.popPose();
        }
    }
    private static void draw(CachedMesh mesh, RenderType type, Matrix4f modelView) {
        type.setupRenderState();
        try { mesh.buffer.bind(); mesh.buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), HologramRenderTypes.shader()); }
        finally { type.clearRenderState(); }
    }
    private static void rotate(PoseStack pose, float angle, float x, float y, float z) {
        if (!Float.isFinite(angle) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || angle == 0) return;
        Vector3f axis = new Vector3f(x, y, z);
        if (axis.lengthSquared() > 0 && Float.isFinite(axis.lengthSquared()))
            pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(angle), axis.normalize()));
    }
    private static void refresh(CachedMesh mesh, Hologram hologram) {
        if (mesh.revision == hologram.renderRevision) return;
        int[] volume, palette;
        synchronized (hologram) {
            mesh.revision = hologram.renderRevision;
            volume = hologram.volume.clone(); palette = hologram.colors.clone();
        }
        // Scale, power and rotation packets do not require another VBO upload.
        if (Arrays.equals(volume, mesh.volume) && Arrays.equals(palette, mesh.palette)) return;
        mesh.volume = volume; mesh.palette = palette; mesh.failed = false;
        try (ByteBufferBuilder memory = new ByteBufferBuilder(64 * 1024)) {
            BufferBuilder builder = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            for (int x = 0; x < Hologram.WIDTH; x++) for (int z = 0; z < Hologram.WIDTH; z++) for (int y = 0; y < Hologram.HEIGHT; y++) {
                int value = value(volume, x, y, z);
                if (value == 0) continue;
                int color = palette[Math.min(value - 1, palette.length - 1)];
                for (Face face : FACES) {
                    if (value(volume, x + face.dx, y + face.dy, z + face.dz) != 0) continue;
                    for (float[] vertex : face.vertices)
                        builder.addVertex(x + vertex[0], y + vertex[1], z + vertex[2]).setUv(vertex[3], vertex[4])
                                .setColor((color >>> 16) & 255, (color >>> 8) & 255, color & 255, 255).setNormal(face.dx, face.dy, face.dz);
                }
            }
            MeshData data = builder.build();
            mesh.empty = data == null;
            if (data == null) { mesh.close(); return; }
            if (mesh.buffer == null) mesh.buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            mesh.buffer.bind(); mesh.buffer.upload(data); VertexBuffer.unbind();
        } catch (OutOfMemoryError error) {
            mesh.close(); mesh.failed = true; mesh.empty = true;
            OpenComputers.LOGGER.warn("Not enough memory to upload a hologram projection", error);
        }
    }
    private static int value(int[] volume, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= Hologram.WIDTH || y >= Hologram.HEIGHT || z >= Hologram.WIDTH) return 0;
        int index = x + z * Hologram.WIDTH;
        return ((volume[index] >>> y) & 1) | (((volume[index + Hologram.PLANE] >>> y) & 1) << 1);
    }
    private static void renderFailure(PoseStack pose, MultiBufferSource buffers) {
        Font font = Minecraft.getInstance().font; String text = "Not enough memory";
        pose.pushPose(); pose.translate(.5, .75, .5); pose.scale(1 / 128F, -1 / 128F, 1 / 128F);
        font.drawInBatch(text, -font.width(text) / 2F, 0, -1, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
    public static void forget(HologramBlockEntity entity) {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(() -> forget(entity)); return; }
        CachedMesh mesh = CACHE.remove(entity); if (mesh != null) mesh.close();
    }
    public static void clearCache() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(HologramBlockEntityRenderer::clearCache); return; }
        CACHE.values().forEach(CachedMesh::close); CACHE.clear();
    }
    public static void cleanCache() {
        long now = System.nanoTime();
        var iterator = CACHE.entrySet().iterator();
        while (iterator.hasNext()) { var entry = iterator.next(); if (entry.getKey().isRemoved() || now - entry.getValue().accessed > CACHE_TIMEOUT) { entry.getValue().close(); iterator.remove(); } }
    }
    @Override public boolean shouldRenderOffScreen(HologramBlockEntity entity) { return true; }
    @Override public int getViewDistance() { return (int) Math.ceil(Settings.get().hologramRenderDistance); }
    @Override public boolean shouldRender(HologramBlockEntity entity, Vec3 camera) { return camera.distanceToSqr(Vec3.atCenterOf(entity.getBlockPos())) < entity.renderDistanceSquared(false); }
    @Override public AABB getRenderBoundingBox(HologramBlockEntity entity) { return entity.projectionBounds(); }
}
