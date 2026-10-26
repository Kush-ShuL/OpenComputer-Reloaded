package li.cil.oc.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import li.cil.oc.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

/** OC text keeps its own filter and depth-write policy instead of inheriting menu/font globals. */
public final class ScreenRenderTypes {
    private record TextureKey(ResourceLocation location, boolean font) {}
    private static final Map<TextureKey,RenderType> TEXTURES=new HashMap<>();
    public static final RenderType BACKGROUND=RenderType.create("opencomputers_screen_background",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,VertexFormat.Mode.QUADS,256,false,false,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_TEXT_BACKGROUND_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP).setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private ScreenRenderTypes() {}
    public static RenderType texture(ResourceLocation location,boolean font) {
        return TEXTURES.computeIfAbsent(new TextureKey(location,font),key->RenderType.create("opencomputers_screen_texture",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,VertexFormat.Mode.QUADS,256,false,false,
                RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_TEXT_SHADER)
                        .setTextureState(new RenderStateShard.EmptyTextureStateShard(()->{
                            var texture=Minecraft.getInstance().getTextureManager().getTexture(key.location());
                            texture.bind();texture.setFilter(false,false);
                            // The original font uses configurable MIN and always-nearest MAG.
                            GlStateManager._texParameter(3553,10241,key.font()&&Settings.get().textLinearFiltering?9729:9728);
                            GlStateManager._texParameter(3553,10240,9728);
                            RenderSystem.setShaderTexture(0,key.location());
                        },()->{}))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(RenderStateShard.LIGHTMAP).setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false)));
    }
}
