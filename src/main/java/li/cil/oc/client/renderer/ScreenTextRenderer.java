package li.cil.oc.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.util.FontUtils;
import li.cil.oc.util.PackedColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Original Unifont/CP437 glyphs rendered with current vertex buffers, never OpenGL display lists. */
public final class ScreenTextRenderer implements ResourceManagerReloadListener {
    public static final ScreenTextRenderer INSTANCE = new ScreenTextRenderer();
    private static final ResourceLocation HEX = ResourceLocation.fromNamespaceAndPath("opencomputers", "font.hex");
    private final Map<Integer, byte[]> glyphBits = new HashMap<>();
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final List<Atlas> atlases = new ArrayList<>();
    private String staticCharacters = "?";
    private int staticWidth = 10, staticHeight = 18;
    private boolean loaded;
    private record Glyph(ResourceLocation texture, float u0, float v0, float u1, float v1, float width, float height, float offsetX, float offsetY) {}
    private static final class Atlas {
        final ResourceLocation location;
        final DynamicTexture texture;
        int count;
        boolean dirty;
        Atlas(int index) {
            location = ResourceLocation.fromNamespaceAndPath("opencomputers", "dynamic/screen_font_" + index);
            texture = new DynamicTexture(256, 256, true);
            texture.getPixels().fillRect(0, 0, 256, 256, 0);
            texture.setFilter(false, false);
            Minecraft.getInstance().getTextureManager().register(location, texture);
        }
    }
    private ScreenTextRenderer() {}
    private boolean textureFont() { return "texture".equals(Settings.get().fontRenderer); }
    public static int characterWidth() { INSTANCE.ensureLoaded(); return INSTANCE.textureFont() ? INSTANCE.staticWidth / 2 : 4; }
    public static int characterHeight() { INSTANCE.ensureLoaded(); return INSTANCE.textureFont() ? INSTANCE.staticHeight / 2 : 8; }
    private void ensureLoaded() { if (!loaded) onResourceManagerReload(Minecraft.getInstance().getResourceManager()); }
    @Override public void onResourceManagerReload(ResourceManager resources) {
        for (Atlas atlas : atlases) Minecraft.getInstance().getTextureManager().release(atlas.location);
        atlases.clear(); glyphs.clear(); glyphBits.clear();
        for (var resource : resources.getResourceStack(HEX)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    int colon = line.indexOf(':'); if (colon < 1) continue;
                    try {
                        int codePoint = Integer.parseInt(line.substring(0, colon), 16);
                        byte[] bits = HexFormat.of().parseHex(line.substring(colon + 1));
                        if (Character.isValidCodePoint(codePoint) && bits.length == 16 * FontUtils.wcwidth(codePoint)) glyphBits.put(codePoint, bits);
                    } catch (IllegalArgumentException ignored) { }
                }
            } catch (Exception error) { OpenComputers.LOGGER.warn("Failed reading OpenComputers Unicode font", error); }
        }
        var metadata = ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/font/chars.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resources.getResourceOrThrow(metadata).open(), StandardCharsets.UTF_8))) {
            staticCharacters = reader.readLine(); String size = reader.readLine();
            if (size != null) { String[] values = size.trim().split("\\s+"); staticWidth = Integer.parseInt(values[0]); staticHeight = Integer.parseInt(values[1]); }
        } catch (Exception error) { OpenComputers.LOGGER.warn("Failed reading texture-font metadata", error); }
        loaded = true;
    }
    private Glyph glyph(int codePoint) {
        if (textureFont()) {
            int index = staticCharacters.indexOf(codePoint); if (index < 0) index = Math.max(0, staticCharacters.indexOf('?'));
            int columns = 256 / staticWidth, x = index % columns * staticWidth, y = index / columns * (staticHeight + 1);
            float scale = (float) Settings.get().fontCharScale;
            return new Glyph(ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/font/" + (Settings.get().textAntiAlias ? "chars.png" : "chars_aliased.png")),
                    x / 256F, y / 256F, (x + staticWidth) / 256F, (y + staticHeight) / 256F,
                    staticWidth * (2 * scale - 1) / 2, staticHeight * (2 * scale - 1) / 2,
                    -staticWidth * (scale - 1) / 2, -staticHeight * (scale - 1) / 2);
        }
        Glyph existing = glyphs.get(codePoint); if (existing != null) return existing;
        byte[] bits = glyphBits.get(codePoint);
        if (bits == null) {
            if (codePoint == '?') return null;
            Glyph fallback = glyph('?'); if (fallback != null) glyphs.put(codePoint, fallback); return fallback;
        }
        Atlas atlas;
        if (atlases.isEmpty() || atlases.getLast().count == 14 * 14) { atlas = new Atlas(atlases.size()); atlases.add(atlas); }
        else atlas = atlases.getLast();
        int slot = atlas.count++, x = slot % 14 * 18 + 1, y = slot / 14 * 18 + 1, width = bits.length / 16 * 8;
        NativeImage image = atlas.texture.getPixels();
        int bytesPerRow = width / 8;
        for (int dy = 0; dy < 16; dy++) for (int dx = 0; dx < width; dx++)
            if ((bits[dy * bytesPerRow + dx / 8] & 1 << (7 - dx % 8)) != 0) image.setPixelRGBA(x + dx, y + dy, 0xFFFFFFFF);
        Glyph glyph = new Glyph(atlas.location, x / 256F, y / 256F, (x + width) / 256F, (y + 16) / 256F, width / 2F, 8, 0, 0);
        glyphs.put(codePoint, glyph); atlas.dirty = true; return glyph;
    }
    public static boolean draw(TextBuffer buffer, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        return draw(buffer, pose, buffers, packedLight, 1, true);
    }
    public static boolean draw(TextBuffer buffer, PoseStack pose, MultiBufferSource buffers, int packedLight, float alpha, boolean blackBackdrop) {
        INSTANCE.ensureLoaded();
        var data = buffer.data(); int w = Math.min(buffer.getViewportWidth(), data.width), h = Math.min(buffer.getViewportHeight(), data.height);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (data.buffer[y][x] != ' ') INSTANCE.glyph(data.buffer[y][x]);
        for (Atlas atlas : INSTANCE.atlases) if (atlas.dirty) { atlas.texture.upload(); atlas.dirty = false; }
        int cw = characterWidth(), ch = characterHeight();
        VertexConsumer background = buffers.getBuffer(ScreenRenderTypes.BACKGROUND);
        if (blackBackdrop) background(background, pose, 0, 0, w * cw, h * ch, 0, 0, alpha);
        for (int y = 0; y < h; y++) {
            int start = 0, previous = PackedColor.unpackBackground(data.color[y][0], data.getFormat());
            for (int x = 1; x <= w; x++) {
                int color = x == w ? -1 : PackedColor.unpackBackground(data.color[y][x], data.getFormat());
                if (color != previous) {
                    if (previous != 0) background(background, pose, start * cw, y * ch, x * cw, (y + 1) * ch, previous, 0.001F, alpha);
                    start = x; previous = color;
                }
            }
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int codePoint = data.buffer[y][x]; if (codePoint == ' ') continue;
            Glyph glyph = INSTANCE.glyph(codePoint); if (glyph == null) continue;
            int color = PackedColor.unpackForeground(data.color[y][x], data.getFormat());
            VertexConsumer vertices = buffers.getBuffer(ScreenRenderTypes.texture(glyph.texture, true));
            float x0 = x * cw + glyph.offsetX, y0 = y * ch + glyph.offsetY;
            textured(vertices, pose, x0, y0, x0 + glyph.width, y0 + glyph.height, color, glyph, 0.002F, alpha);
        }
        boolean changed = buffer.dirty; buffer.dirty = false; return changed;
    }
    private static void background(VertexConsumer out, PoseStack pose, float x0, float y0, float x1, float y1, int color, float z, float alpha) {
        int rgba = (Math.clamp((int)(alpha * 255), 0, 255) << 24) | color;
        out.addVertex(pose.last(), x0, y1, z).setColor(rgba).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(pose.last(), x1, y1, z).setColor(rgba).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(pose.last(), x1, y0, z).setColor(rgba).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(pose.last(), x0, y0, z).setColor(rgba).setLight(LightTexture.FULL_BRIGHT);
    }
    private static void textured(VertexConsumer out, PoseStack pose, float x0, float y0, float x1, float y1, int color, Glyph glyph, float z, float alpha) {
        int rgba = (Math.clamp((int)(alpha * 255), 0, 255) << 24) | color;
        out.addVertex(pose.last(), x0, y1, z).setColor(rgba).setUv(glyph.u0, glyph.v1).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(pose.last(), x1, y1, z).setColor(rgba).setUv(glyph.u1, glyph.v1).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(pose.last(), x1, y0, z).setColor(rgba).setUv(glyph.u1, glyph.v0).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(pose.last(), x0, y0, z).setColor(rgba).setUv(glyph.u0, glyph.v0).setLight(LightTexture.FULL_BRIGHT);
    }
}
