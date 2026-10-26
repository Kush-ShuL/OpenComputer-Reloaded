package li.cil.oc.util;

import li.cil.oc.Settings;
import li.cil.oc.api.Persistable;
import li.cil.oc.api.internal.TextBuffer.ColorDepth;
import net.minecraft.nbt.CompoundTag;

/** Original OpenComputers 1/4/8-bit palette and RGB-cube color encoding. */
public final class PackedColor {
    public static final int ForegroundShift = 8;
    public static final int BackgroundMask = 0xFF;
    private PackedColor() {}

    public record Color(int value, boolean isPalette) {
        public Color(int value) { this(value, false); }
    }

    public static final class Depth {
        private Depth() {}
        public static int bits(ColorDepth depth) {
            return switch (depth) { case OneBit -> 1; case FourBit -> 4; case EightBit -> 8; };
        }
        public static ColorFormat format(ColorDepth depth) {
            return switch (depth) {
                case OneBit -> new SingleBitFormat(Settings.get().monochromeColor);
                case FourBit -> new MutablePaletteFormat();
                case EightBit -> new HybridFormat();
            };
        }
    }

    public interface ColorFormat extends Persistable {
        ColorDepth depth();
        int inflate(int value);
        byte deflate(Color value);
        default void validate(Color value) {
            if (value.isPalette()) throw new IllegalArgumentException("color palette not supported");
        }
        default boolean isFromPalette(int value) { return false; }
        @Override default void load(CompoundTag nbt) {}
        @Override default void save(CompoundTag nbt) {}
    }

    public static class SingleBitFormat implements ColorFormat {
        public final int color;
        public SingleBitFormat(int color) { this.color = color; }
        @Override public ColorDepth depth() { return ColorDepth.OneBit; }
        @Override public int inflate(int value) { return value == 0 ? 0 : color; }
        @Override public byte deflate(Color value) { return (byte) (value.value() == 0 ? 0 : 1); }
    }

    public abstract static class PaletteFormat implements ColorFormat {
        protected abstract int[] palette();
        @Override public int inflate(int value) {
            int[] palette = palette();
            return palette[Math.clamp(value, 0, palette.length - 1)];
        }
        @Override public void validate(Color value) {
            if (value.isPalette() && (value.value() < 0 || value.value() >= palette().length))
                throw new IllegalArgumentException("invalid palette index");
        }
        @Override public byte deflate(Color value) {
            int[] palette = palette();
            if (value.isPalette()) return (byte) (Math.max(0, value.value()) % palette.length);
            double best = Double.POSITIVE_INFINITY;
            int index = 0;
            for (int i = 0; i < palette.length; i++) {
                double distance = delta(value.value(), palette[i]);
                if (distance < best) { best = distance; index = i; }
            }
            return (byte) index;
        }
        @Override public boolean isFromPalette(int value) { return true; }
        protected static double delta(int a, int b) {
            int r = ((a >>> 16) & 255) - ((b >>> 16) & 255);
            int g = ((a >>> 8) & 255) - ((b >>> 8) & 255);
            int blue = (a & 255) - (b & 255);
            return 0.2126 * r * r + 0.7152 * g * g + 0.0722 * blue * blue;
        }
    }

    public static class MutablePaletteFormat extends PaletteFormat {
        protected final int[] palette = {
                0xFFFFFF, 0xFFCC33, 0xCC66CC, 0x6699FF,
                0xFFFF33, 0x33CC33, 0xFF6699, 0x333333,
                0xCCCCCC, 0x336699, 0x9933CC, 0x333399,
                0x663300, 0x336600, 0xFF3333, 0x000000};
        @Override public ColorDepth depth() { return ColorDepth.FourBit; }
        @Override protected int[] palette() { return palette; }
        public int get(int index) { return palette[index]; }
        public void set(int index, int value) { palette[index] = value; }
        @Override public void load(CompoundTag nbt) {
            int[] loaded = nbt.getIntArray("palette");
            System.arraycopy(loaded, 0, palette, 0, Math.min(loaded.length, palette.length));
        }
        @Override public void save(CompoundTag nbt) { nbt.putIntArray("palette", palette.clone()); }
    }

    public static class HybridFormat extends MutablePaletteFormat {
        private static final int REDS = 6, GREENS = 8, BLUES = 5;
        private final int[] staticPalette = new int[REDS * GREENS * BLUES];
        public HybridFormat() {
            for (int index = 0; index < staticPalette.length; index++) {
                int b = (int) ((index % BLUES) * 255 / (BLUES - 1.0) + 0.5);
                int g = (int) ((index / BLUES % GREENS) * 255 / (GREENS - 1.0) + 0.5);
                int r = (int) ((index / BLUES / GREENS % REDS) * 255 / (REDS - 1.0) + 0.5);
                staticPalette[index] = (r << 16) | (g << 8) | b;
            }
            for (int i = 0; i < palette.length; i++) {
                int shade = 255 * (i + 1) / (palette.length + 1);
                palette[i] = (shade << 16) | (shade << 8) | shade;
            }
        }
        @Override public ColorDepth depth() { return ColorDepth.EightBit; }
        @Override public int inflate(int value) {
            return isFromPalette(value) ? super.inflate(value) : staticPalette[(value - palette.length) % staticPalette.length];
        }
        @Override public byte deflate(Color value) {
            byte paletteIndex = super.deflate(value);
            if (value.isPalette()) return paletteIndex;
            int rgb = value.value();
            int r = (int) (((rgb >>> 16) & 255) * (REDS - 1.0) / 255 + 0.5);
            int g = (int) (((rgb >>> 8) & 255) * (GREENS - 1.0) / 255 + 0.5);
            int b = (int) ((rgb & 255) * (BLUES - 1.0) / 255 + 0.5);
            byte cube = (byte) (palette.length + r * GREENS * BLUES + g * BLUES + b);
            return delta(inflate(cube & 255), rgb) < delta(inflate(paletteIndex & 255), rgb) ? cube : paletteIndex;
        }
        @Override public boolean isFromPalette(int value) { return value >= 0 && value < palette.length; }
    }

    public static short pack(Color foreground, Color background, ColorFormat format) {
        return (short) (((format.deflate(foreground) & 255) << ForegroundShift) | (format.deflate(background) & 255));
    }
    public static int extractForeground(short color) { return (color & 0xFFFF) >>> ForegroundShift; }
    public static int extractBackground(short color) { return color & BackgroundMask; }
    public static int unpackForeground(short color, ColorFormat format) { return format.inflate(extractForeground(color)); }
    public static int unpackBackground(short color, ColorFormat format) { return format.inflate(extractBackground(color)); }
}
