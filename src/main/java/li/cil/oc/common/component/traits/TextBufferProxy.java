package li.cil.oc.common.component.traits;

import li.cil.oc.api.internal.TextBuffer;
import li.cil.oc.util.ExtendedUnicodeHelper;
import li.cil.oc.util.PackedColor;

/** Complete API view over the shared text/color cell algorithms. */
public interface TextBufferProxy extends TextBuffer {
    li.cil.oc.util.TextBuffer data();
    @Override default int getWidth() { return data().width; }
    @Override default int getHeight() { return data().height; }
    @Override default boolean setColorDepth(ColorDepth depth) {
        if (depth.ordinal() > getMaximumColorDepth().ordinal()) throw new IllegalArgumentException("unsupported depth");
        return data().setFormat(PackedColor.Depth.format(depth));
    }
    @Override default ColorDepth getColorDepth() { return data().getFormat().depth(); }
    default void onBufferPaletteChange(int index) {}
    @Override default void setPaletteColor(int index, int color) {
        if (!(data().getFormat() instanceof PackedColor.MutablePaletteFormat palette)) throw new IllegalStateException("palette not available");
        palette.set(index, color);
        onBufferPaletteChange(index);
    }
    @Override default int getPaletteColor(int index) {
        if (!(data().getFormat() instanceof PackedColor.MutablePaletteFormat palette)) throw new IllegalStateException("palette not available");
        return palette.get(index);
    }
    default void onBufferColorChange() {}
    @Override default void setForegroundColor(int color) { setForegroundColor(color, false); }
    @Override default void setForegroundColor(int color, boolean isFromPalette) {
        PackedColor.Color value = new PackedColor.Color(color, isFromPalette);
        if (!value.equals(data().getForeground())) { data().setForeground(value); onBufferColorChange(); }
    }
    @Override default int getForegroundColor() { return data().getForeground().value(); }
    @Override default boolean isForegroundFromPalette() { return data().getForeground().isPalette(); }
    @Override default void setBackgroundColor(int color) { setBackgroundColor(color, false); }
    @Override default void setBackgroundColor(int color, boolean isFromPalette) {
        PackedColor.Color value = new PackedColor.Color(color, isFromPalette);
        if (!value.equals(data().getBackground())) { data().setBackground(value); onBufferColorChange(); }
    }
    @Override default int getBackgroundColor() { return data().getBackground().value(); }
    @Override default boolean isBackgroundFromPalette() { return data().getBackground().isPalette(); }
    default void onBufferCopy(int col, int row, int w, int h, int tx, int ty) {}
    @Override default void copy(int col, int row, int w, int h, int tx, int ty) {
        if (data().copy(col, row, w, h, tx, ty)) onBufferCopy(col, row, w, h, tx, ty);
    }
    default void onBufferFill(int col, int row, int w, int h, int codePoint) {}
    @Override default void fill(int col, int row, int w, int h, char c) { fill(col, row, w, h, (int) c); }
    @Override default void fill(int col, int row, int w, int h, int c) {
        if (data().fill(col, row, w, h, c)) onBufferFill(col, row, w, h, c);
    }
    default void onBufferSet(int col, int row, String value, boolean vertical) {}
    @Override default void set(int col, int row, String value, boolean vertical) {
        int length = ExtendedUnicodeHelper.length(value);
        if (vertical) {
            if (col < 0 || col >= getWidth() || row >= getHeight()) return;
            int skip = Math.max(0, -row), y = Math.max(0, row);
            String text = truncate(value, length, skip, getHeight() - y);
            if (data().set(col, y, text, true)) onBufferSet(col, y, text, true);
        } else {
            if (row < 0 || row >= getHeight() || col >= getWidth()) return;
            int skip = Math.max(0, -col), x = Math.max(0, col);
            String text = truncate(value, length, skip, getWidth() - x);
            if (data().set(x, row, text, false)) onBufferSet(x, row, text, false);
        }
    }
    private static String truncate(String text, int length, int skip, int maxWidth) {
        int count = Math.min(length - skip, maxWidth);
        if (count <= 0) return "";
        int start = text.offsetByCodePoints(0, skip);
        return text.substring(start, text.offsetByCodePoints(start, count));
    }
    @Override default char get(int col, int row) { return (char) data().get(col, row); }
    @Override default int getCodePoint(int col, int row) { return data().get(col, row); }
    private short colorAt(int col, int row) { data().checkCell(col, row); return data().color[row][col]; }
    @Override default int getForegroundColor(int col, int row) {
        short color = colorAt(col, row);
        return isForegroundFromPalette(col, row) ? PackedColor.extractForeground(color) : PackedColor.unpackForeground(color, data().getFormat());
    }
    @Override default boolean isForegroundFromPalette(int col, int row) {
        return data().getFormat().isFromPalette(PackedColor.extractForeground(colorAt(col, row)));
    }
    @Override default int getBackgroundColor(int col, int row) {
        short color = colorAt(col, row);
        return isBackgroundFromPalette(col, row) ? PackedColor.extractBackground(color) : PackedColor.unpackBackground(color, data().getFormat());
    }
    @Override default boolean isBackgroundFromPalette(int col, int row) {
        return data().getFormat().isFromPalette(PackedColor.extractBackground(colorAt(col, row)));
    }
    @Override default void rawSetText(int col, int row, char[][] text) {
        int[][] codePoints = new int[text.length][];
        for (int y = 0; y < text.length; y++) {
            codePoints[y] = new int[text[y].length];
            for (int x = 0; x < text[y].length; x++) codePoints[y][x] = text[y][x];
        }
        rawSetText(col, row, codePoints);
    }
    @Override default void rawSetText(int col, int row, int[][] text) {
        for (int y = Math.max(0, row); y < Math.min((long) row + text.length, getHeight()); y++) {
            int skip = Math.max(0, -col), target = Math.max(0, col);
            int count = Math.min(text[y - row].length - skip, getWidth() - target);
            if (count > 0) System.arraycopy(text[y - row], skip, data().buffer[y], target, count);
        }
    }
    @Override default void rawSetForeground(int col, int row, int[][] color) { rawSetColor(col, row, color, true); }
    @Override default void rawSetBackground(int col, int row, int[][] color) { rawSetColor(col, row, color, false); }
    private void rawSetColor(int col, int row, int[][] color, boolean foreground) {
        for (int y = Math.max(0, row); y < Math.min((long) row + color.length, getHeight()); y++) {
            int[] values = color[y - row];
            for (int x = Math.max(0, col); x < Math.min((long) col + values.length, getWidth()); x++) {
                int encoded = data().getFormat().deflate(new PackedColor.Color(values[x - col])) & 255;
                int previous = data().color[y][x] & 0xFFFF;
                data().color[y][x] = (short) (foreground ? (previous & 255) | encoded << 8 : (previous & 0xFF00) | encoded);
            }
        }
    }
}
