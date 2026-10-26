package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;

/** The original depth pre-pass and SRC_ALPHA/ONE projection, expressed as modern render states. */
public final class HologramRenderTypes {
    public static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/model/hologram_effect.png");
    private static ShaderInstance shader;
    private static final RenderType DEPTH = create(true, false), COLOR = create(false, false);
    private static final RenderType DEPTH_INSIDE = create(true, true), COLOR_INSIDE = create(false, true);
    private HologramRenderTypes() {}
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("opencomputers", "hologram"),
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL), loaded -> shader = loaded);
    }
    public static ShaderInstance shader() { return shader; }
    public static RenderType depth(boolean inside) { return inside ? DEPTH_INSIDE : DEPTH; }
    public static RenderType color(boolean inside) { return inside ? COLOR_INSIDE : COLOR; }
    private static RenderType create(boolean depth, boolean inside) {
        return RenderType.create("opencomputers_hologram_" + (depth ? "depth" : "color") + (inside ? "_inside" : ""),
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS, 256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(HologramRenderTypes::shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(EFFECT, false, false))
                        .setTransparencyState(depth ? RenderStateShard.NO_TRANSPARENCY : RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(depth ? RenderStateShard.LEQUAL_DEPTH_TEST : RenderStateShard.EQUAL_DEPTH_TEST)
                        .setWriteMaskState(depth ? RenderStateShard.DEPTH_WRITE : RenderStateShard.COLOR_WRITE)
                        .setCullState(inside ? RenderStateShard.NO_CULL : RenderStateShard.CULL)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .createCompositeState(false));
    }
}
