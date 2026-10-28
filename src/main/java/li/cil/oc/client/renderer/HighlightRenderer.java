package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Random;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.common.block.CableBlock;
import li.cil.oc.common.block.entity.PrintBlockEntity;
import li.cil.oc.common.printing.PrintGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** Tablet target projection and the original per-shape print/cable selection outlines. */
@EventBusSubscriber(modid = OpenComputers.ID, value = Dist.CLIENT)
public final class HighlightRenderer {
    private static final Random RANDOM = new Random();
    private static final double EXPAND = .002, MIN = 6.0 / 16 - EXPAND, MAX = 10.0 / 16 + EXPAND;
    private static final RenderType PROJECTION = RenderType.create("opencomputers_tablet_target",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(HologramRenderTypes.EFFECT, false, false))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private HighlightRenderer() {}

    @SubscribeEvent
    public static void highlight(RenderHighlightEvent.Block event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        var pos = event.getTarget().getBlockPos();
        var state = client.level.getBlockState(pos);
        if (state.isAir()) return;
        var camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        try {
            var tablet = li.cil.oc.api.Items.get("tablet");
            var shape = state.getShape(client.level, pos);
            if (tablet != null && li.cil.oc.api.Items.get(client.player.getMainHandItem()) == tablet && !shape.isEmpty()) {
                pose.pushPose();
                pose.scale(1.002f, 1.002f, 1.002f);
                Direction side = event.getTarget().getDirection();
                if (Settings.get().hologramFlickerFrequency > 0 && RANDOM.nextDouble() < Settings.get().hologramFlickerFrequency) {
                    pose.scale((float) (1 + RANDOM.nextGaussian() * .01), (float) (1 + RANDOM.nextGaussian() * .001), (float) (1 + RANDOM.nextGaussian() * .01));
                    pose.translate(RANDOM.nextGaussian() * .01 * (1 - Math.abs(side.getStepX())),
                            RANDOM.nextGaussian() * .01 * (1 - Math.abs(side.getStepY())),
                            RANDOM.nextGaussian() * .01 * (1 - Math.abs(side.getStepZ())));
                }
                projection(pose, event.getMultiBufferSource().getBuffer(PROJECTION), shape.bounds(), side);
                pose.popPose();
            }
            if (client.level.getBlockEntity(pos) instanceof PrintBlockEntity print) {
                var shapes = print.active ? print.data.stateOn : print.data.stateOff;
                if (!shapes.isEmpty()) {
                    VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
                    for (var part : shapes) LevelRenderer.renderLineBox(pose, lines, PrintGeometry.rotate(part.bounds, print.facing()).inflate(EXPAND), 0, 0, 0, .4f);
                    event.setCanceled(true);
                }
            } else if (state.getBlock() instanceof CableBlock) {
                int mask = 0;
                for (Direction side : Direction.values()) if (state.getValue(CableBlock.CONNECTIONS[side.ordinal()])) mask |= 1 << side.ordinal();
                cable(pose, event.getMultiBufferSource().getBuffer(RenderType.lines()), mask);
                event.setCanceled(true);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void projection(PoseStack pose, VertexConsumer vertices, AABB b, Direction side) {
        switch (side) {
            case UP -> {
                vertex(pose, vertices, b.maxX, b.maxY + EXPAND, b.maxZ, b.maxZ, b.maxX);
                vertex(pose, vertices, b.maxX, b.maxY + EXPAND, b.minZ, b.minZ, b.maxX);
                vertex(pose, vertices, b.minX, b.maxY + EXPAND, b.minZ, b.minZ, b.minX);
                vertex(pose, vertices, b.minX, b.maxY + EXPAND, b.maxZ, b.maxZ, b.minX);
            }
            case DOWN -> {
                vertex(pose, vertices, b.maxX, b.minY - EXPAND, b.minZ, b.minZ, b.maxX);
                vertex(pose, vertices, b.maxX, b.minY - EXPAND, b.maxZ, b.maxZ, b.maxX);
                vertex(pose, vertices, b.minX, b.minY - EXPAND, b.maxZ, b.maxZ, b.minX);
                vertex(pose, vertices, b.minX, b.minY - EXPAND, b.minZ, b.minZ, b.minX);
            }
            case EAST, WEST -> {
                double x = side == Direction.EAST ? b.maxX + EXPAND : b.minX - EXPAND;
                double a = side == Direction.EAST ? b.minZ : b.maxZ, z = side == Direction.EAST ? b.maxZ : b.minZ;
                vertex(pose, vertices, x, b.maxY, a, a, b.maxY);
                vertex(pose, vertices, x, b.maxY, z, z, b.maxY);
                vertex(pose, vertices, x, b.minY, z, z, b.minY);
                vertex(pose, vertices, x, b.minY, a, a, b.minY);
            }
            case SOUTH, NORTH -> {
                double z = side == Direction.SOUTH ? b.maxZ + EXPAND : b.minZ - EXPAND;
                double a = side == Direction.SOUTH ? b.maxX : b.minX, x = side == Direction.SOUTH ? b.minX : b.maxX;
                vertex(pose, vertices, a, b.maxY, z, a, b.maxY);
                vertex(pose, vertices, x, b.maxY, z, x, b.maxY);
                vertex(pose, vertices, x, b.minY, z, x, b.minY);
                vertex(pose, vertices, a, b.minY, z, a, b.minY);
            }
        }
    }

    private static void vertex(PoseStack pose, VertexConsumer vertices, double x, double y, double z, double u, double v) {
        vertices.addVertex(pose.last(), (float) x, (float) y, (float) z).setUv((float) (u * 16), (float) (v * 16)).setColor(0, 255, 0, 102);
    }

    private static void cable(PoseStack pose, VertexConsumer vertices, int mask) {
        for (Direction side : Direction.values()) if ((mask & 1 << side.ordinal()) != 0) {
            double end = side.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? -EXPAND : 1 + EXPAND;
            double center = side.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? MIN : MAX;
            adjacent(pose, vertices, side.getAxis(), end, MIN, MIN, MIN, MAX);
            adjacent(pose, vertices, side.getAxis(), end, MIN, MAX, MAX, MAX);
            adjacent(pose, vertices, side.getAxis(), end, MAX, MAX, MAX, MIN);
            adjacent(pose, vertices, side.getAxis(), end, MAX, MIN, MIN, MIN);
            along(pose, vertices, side.getAxis(), MIN, MIN, end, center);
            along(pose, vertices, side.getAxis(), MAX, MIN, end, center);
            along(pose, vertices, side.getAxis(), MAX, MAX, end, center);
            along(pose, vertices, side.getAxis(), MIN, MAX, end, center);
        }
        for (Direction a : new Direction[]{Direction.WEST, Direction.EAST}) {
            for (Direction b : new Direction[]{Direction.DOWN, Direction.UP}) core(pose, vertices, mask, a, b, Direction.Axis.Z);
            for (Direction b : new Direction[]{Direction.NORTH, Direction.SOUTH}) core(pose, vertices, mask, a, b, Direction.Axis.Y);
        }
        for (Direction a : new Direction[]{Direction.DOWN, Direction.UP}) for (Direction b : new Direction[]{Direction.NORTH, Direction.SOUTH}) core(pose, vertices, mask, a, b, Direction.Axis.X);
    }

    private static void core(PoseStack pose, VertexConsumer vertices, int mask, Direction a, Direction b, Direction.Axis axis) {
        if (((mask >> a.ordinal()) & 1) != ((mask >> b.ordinal()) & 1)) return;
        along(pose, vertices, axis, a.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? MIN : MAX,
                b.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? MIN : MAX, MIN, MAX);
    }

    private static void along(PoseStack pose, VertexConsumer vertices, Direction.Axis axis, double a, double b, double start, double end) {
        switch (axis) {
            case X -> line(pose, vertices, start, a, b, end, a, b);
            case Y -> line(pose, vertices, a, start, b, a, end, b);
            case Z -> line(pose, vertices, a, b, start, a, b, end);
        }
    }

    private static void adjacent(PoseStack pose, VertexConsumer vertices, Direction.Axis axis, double offset, double a, double b, double c, double d) {
        switch (axis) {
            case X -> line(pose, vertices, offset, a, b, offset, c, d);
            case Y -> line(pose, vertices, a, offset, b, c, offset, d);
            case Z -> line(pose, vertices, a, b, offset, c, d, offset);
        }
    }

    private static void line(PoseStack pose, VertexConsumer vertices, double x, double y, double z, double x2, double y2, double z2) {
        float dx = (float) (x2 - x), dy = (float) (y2 - y), dz = (float) (z2 - z);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        vertices.addVertex(pose.last(), (float) x, (float) y, (float) z).setColor(0, 0, 0, .4f).setNormal(pose.last(), dx / length, dy / length, dz / length);
        vertices.addVertex(pose.last(), (float) x2, (float) y2, (float) z2).setColor(0, 0, 0, .4f).setNormal(pose.last(), dx / length, dy / length, dz / length);
    }
}
