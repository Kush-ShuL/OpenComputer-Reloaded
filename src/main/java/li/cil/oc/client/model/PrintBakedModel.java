package li.cil.oc.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import li.cil.oc.common.block.entity.PrintBlockEntity;
import li.cil.oc.common.item.data.PrintData;
import li.cil.oc.common.printing.PrintGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Real shape-dependent quads, with per-shape atlas sprites and packed vertex tint. */
public final class PrintBakedModel implements IDynamicBakedModel {
    private final BakedModel base;
    private final PrintGeometry.View fixed;
    private final Map<PrintGeometry.View, List<BakedQuad>> cache;
    private final ItemOverrides overrides;
    public PrintBakedModel(BakedModel base) { this(base, null, Collections.synchronizedMap(new WeakHashMap<>())); }
    private PrintBakedModel(BakedModel base, PrintGeometry.View fixed, Map<PrintGeometry.View, List<BakedQuad>> cache) {
        this.base = base; this.fixed = fixed; this.cache = cache;
        overrides = fixed == null ? new ItemOverrides() {
            @Override public BakedModel resolve(BakedModel original, ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
                PrintData data = new PrintData(stack);
                PrintGeometry.View view = PrintGeometry.view(data, data.hasActiveState() && extendedTooltip(), Direction.SOUTH, true);
                return new PrintBakedModel(base, view, cache);
            }
        } : ItemOverrides.EMPTY;
    }
    @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        return level.getBlockEntity(pos) instanceof PrintBlockEntity print ? print.getModelData() : data;
    }
    private static boolean extendedTooltip() {
        var client=Minecraft.getInstance();var key=client.options.keyShift.getKey();long window=client.getWindow().getWindow();
        return switch(key.getType()) {
            case MOUSE -> org.lwjgl.glfw.GLFW.glfwGetMouseButton(window,key.getValue())==org.lwjgl.glfw.GLFW.GLFW_PRESS;
            case KEYSYM -> key.getValue()>=0&&com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,key.getValue());
            default -> false;
        };
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
        if (side != null) return List.of();
        PrintGeometry.View view = fixed == null ? data.get(PrintBlockEntity.MODEL) : fixed;
        if (view == null) return List.of();
        return cache.computeIfAbsent(view, PrintBakedModel::bake);
    }
    private static List<BakedQuad> bake(PrintGeometry.View view) {
        List<BakedQuad> result = new ArrayList<>();
        List<PrintData.Shape> shapes = view.shapes();
        if (shapes.isEmpty() && view.item()) shapes = List.of(new PrintData.Shape(new AABB(0, 0, 0, 1, 1, 1), "opencomputers:blocks/white", Optional.of(0x7FCC19)));
        for (PrintData.Shape shape : shapes) {
            if (!view.item() && shape.texture.isEmpty()) continue;
            TextureAtlasSprite sprite = sprite(shape.texture); AABB b = shape.bounds;
            int rgb = shape.tint.orElse(0xFFFFFF), color = 0xFF000000 | (rgb & 255) << 16 | rgb & 0xFF00 | (rgb >>> 16) & 255;
            for (Direction direction : Direction.values()) result.add(quad(b, direction, sprite, color));
        }
        return List.copyOf(result);
    }
    private static TextureAtlasSprite sprite(String name) {
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        ResourceLocation id = ResourceLocation.tryParse(name);
        TextureAtlasSprite sprite = id == null ? atlas.apply(MissingTextureAtlasSprite.getLocation()) : atlas.apply(id);
        if (!sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) return sprite;
        if (id != null) {
            String path = id.getPath();
            String modern = path.startsWith("blocks/") ? "block/" + path.substring(7) : path.indexOf('/') < 0 ? "block/" + path : path;
            sprite = atlas.apply(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), modern));
            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) && path.indexOf('/') < 0)
                sprite = atlas.apply(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "blocks/" + path));
        }
        return sprite;
    }
    private static BakedQuad quad(AABB b, Direction side, TextureAtlasSprite sprite, int color) {
        double x0 = b.minX, x1 = b.maxX, y0 = b.minY, y1 = b.maxY, z0 = b.minZ, z1 = b.maxZ;
        double[][] points = switch (side) {
            case DOWN -> new double[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}};
            case UP -> new double[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}};
            case NORTH -> new double[][]{{x1,y1,z0},{x1,y0,z0},{x0,y0,z0},{x0,y1,z0}};
            case SOUTH -> new double[][]{{x0,y1,z1},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1}};
            case WEST -> new double[][]{{x0,y1,z0},{x0,y0,z0},{x0,y0,z1},{x0,y1,z1}};
            case EAST -> new double[][]{{x1,y1,z1},{x1,y0,z1},{x1,y0,z0},{x1,y1,z0}};
        };
        double[] uv = switch (side) {
            case DOWN -> new double[]{x0,1-z1,x1,1-z0}; case UP -> new double[]{x0,z0,x1,z1};
            case NORTH -> new double[]{1-x1,1-y1,1-x0,1-y0}; case SOUTH -> new double[]{x0,1-y1,x1,1-y0};
            case WEST -> new double[]{z0,1-y1,z1,1-y0}; case EAST -> new double[]{1-z1,1-y1,1-z0,1-y0};
        };
        int normal = (side.getStepX() * 127 & 255) | (side.getStepY() * 127 & 255) << 8 | (side.getStepZ() * 127 & 255) << 16;
        int[] vertices = new int[32];
        for (int index = 0; index < 4; index++) {
            int offset = index * 8;
            vertices[offset] = Float.floatToRawIntBits((float) points[index][0]); vertices[offset + 1] = Float.floatToRawIntBits((float) points[index][1]); vertices[offset + 2] = Float.floatToRawIntBits((float) points[index][2]); vertices[offset + 3] = color;
            vertices[offset + 4] = Float.floatToRawIntBits(sprite.getU((float) uv[index < 2 ? 0 : 2]));
            vertices[offset + 5] = Float.floatToRawIntBits(sprite.getV((float) uv[index == 0 || index == 3 ? 1 : 3]));
            vertices[offset + 6] = 0; vertices[offset + 7] = normal;
        }
        return new BakedQuad(vertices, -1, side, sprite, true);
    }
    @Override public boolean useAmbientOcclusion() { return base.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return base.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return base.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return overrides; }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) { return ChunkRenderTypeSet.of(RenderType.cutoutMipped()); }
    @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) { return List.of(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)); }
}
