package li.cil.oc.api.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import li.cil.oc.api.component.RackMountable;
import li.cil.oc.api.internal.Rack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Allows integrations to customize the front of a rack mountable.
 * Render handlers receive the rack face's transformed pose and buffer source.
 * The origin is the face's upper left corner and its size is one block.
 */
@OnlyIn(Dist.CLIENT)
public abstract class RackMountableRenderEvent extends Event {
    public final Rack rack;
    public final int mountable;
    /** Additional synchronized data supplied by {@link RackMountable#getData()}. */
    public final CompoundTag data;

    protected RackMountableRenderEvent(Rack rack, int mountable, CompoundTag data) {
        this.rack = rack;
        this.mountable = mountable;
        this.data = data;
    }

    /** Cancel to replace the standard mountable front texture. */
    public static class Block extends RackMountableRenderEvent implements ICancellableEvent {
        public final Direction side;
        private TextureAtlasSprite frontTextureOverride;

        public Block(Rack rack, int mountable, CompoundTag data, Direction side) {
            super(rack, mountable, data);
            this.side = side;
        }

        public TextureAtlasSprite getFrontTextureOverride() {
            return frontTextureOverride;
        }

        public void setFrontTextureOverride(TextureAtlasSprite texture) {
            frontTextureOverride = texture;
        }
    }

    /**
     * Dynamic overlay event. The historical class name is retained for API
     * continuity; rendering now uses the supplied block entity render context.
     */
    public static class TileEntity extends RackMountableRenderEvent {
        public final float v0, v1;
        public final PoseStack poseStack;
        public final MultiBufferSource buffers;
        public final int packedLight;
        public final int packedOverlay;

        public TileEntity(Rack rack, int mountable, CompoundTag data, float v0, float v1,
                          PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
            super(rack, mountable, data);
            this.v0 = v0;
            this.v1 = v1;
            this.poseStack = poseStack;
            this.buffers = buffers;
            this.packedLight = packedLight;
            this.packedOverlay = packedOverlay;
        }

        public void renderOverlay(ResourceLocation texture) {
            renderOverlay(texture, 0, 1);
        }

        /** Draws the mountable's vertical slice from an independent texture. */
        public void renderOverlay(ResourceLocation texture, float u0, float u1) {
            draw(texture, u0, u1, u0, u1, v0, v1, packedLight);
        }

        /** Draw an overlay at a specific light level, e.g. FULL_BRIGHT for LEDs. */
        public void renderOverlay(ResourceLocation texture, float u0, float u1, int light) {
            draw(texture, u0, u1, u0, u1, v0, v1, light);
        }

        public void renderOverlayFromAtlas(ResourceLocation texture) {
            renderOverlayFromAtlas(texture, 0, 1);
        }

        /** Draws the corresponding slice of a block-atlas sprite. */
        public void renderOverlayFromAtlas(ResourceLocation texture, float u0, float u1) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
            float textureU0 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * u0;
            float textureU1 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * u1;
            float textureV0 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * v0;
            float textureV1 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * v1;
            draw(TextureAtlas.LOCATION_BLOCKS, u0, u1, textureU0, textureU1, textureV0, textureV1, packedLight);
        }

        private void draw(ResourceLocation texture, float x0, float x1, float u0, float u1,
                          float textureV0, float textureV1, int light) {
            VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(texture));
            vertex(vertices, x0, v1, u0, textureV1, light);
            vertex(vertices, x1, v1, u1, textureV1, light);
            vertex(vertices, x1, v0, u1, textureV0, light);
            vertex(vertices, x0, v0, u0, textureV0, light);
        }

        private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int light) {
            PoseStack.Pose pose = poseStack.last();
            vertices.addVertex(pose, x, y, -0.002F).setColor(255, 255, 255, 255)
                    .setUv(u, v).setOverlay(packedOverlay).setLight(light).setNormal(pose, 0, 0, -1);
        }
    }
}
