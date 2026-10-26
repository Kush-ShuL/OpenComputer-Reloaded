package li.cil.oc.util;

import li.cil.oc.Settings;
import li.cil.oc.api.Persistable;
import li.cil.oc.api.internal.TextBuffer.ColorDepth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.PrimitiveIterator;

/** Unicode cells, packed colors, overlap-safe copying and the original NBT format. */
public class TextBuffer implements Persistable {
    public int width, height;
    public int[][] buffer;
    public short[][] color;
    private PackedColor.ColorFormat format;
    private PackedColor.Color foreground = new PackedColor.Color(0xFFFFFF);
    private PackedColor.Color background = new PackedColor.Color(0);
    private short packed;

    public TextBuffer(int width, int height, PackedColor.ColorFormat format) {
        this.format = format;
        packed = PackedColor.pack(foreground, background, format);
        setSize(width, height);
    }
    public PackedColor.ColorFormat getFormat() { return format; }
    public PackedColor.Color getForeground() { return foreground; }
    public PackedColor.Color getBackground() { return background; }
    public void setForeground(PackedColor.Color value) {
        format.validate(value);
        foreground = value;
        packed = PackedColor.pack(foreground, background, format);
    }
    public void setBackground(PackedColor.Color value) {
        format.validate(value);
        background = value;
        packed = PackedColor.pack(foreground, background, format);
    }
    public boolean setFormat(PackedColor.ColorFormat value) {
        if (format.depth() == value.depth()) return false;
        for (short[] row : color) {
            for (int x = 0; x < row.length; x++) {
                row[x] = PackedColor.pack(new PackedColor.Color(PackedColor.unpackForeground(row[x], format)),
                        new PackedColor.Color(PackedColor.unpackBackground(row[x], format)), value);
            }
        }
        format = value;
        packed = PackedColor.pack(foreground, background, format);
        return true;
    }
    public boolean setSize(int requestedWidth, int requestedHeight) {
        int w = Math.max(1, requestedWidth), h = Math.max(1, requestedHeight);
        if (width == w && height == h) return false;
        int[][] newBuffer = new int[h][w];
        short[][] newColor = new short[h][w];
        for (int y = 0; y < h; y++) {
            Arrays.fill(newBuffer[y], ' ');
            Arrays.fill(newColor[y], packed);
            if (y < height) {
                System.arraycopy(buffer[y], 0, newBuffer[y], 0, Math.min(w, width));
                System.arraycopy(color[y], 0, newColor[y], 0, Math.min(w, width));
            }
        }
        buffer = newBuffer;
        color = newColor;
        width = w;
        height = h;
        return true;
    }
    public int get(int col, int row) {
        checkCell(col, row);
        return buffer[row][col];
    }
    public void checkCell(int col, int row) {
        if (col < 0 || col >= width || row < 0 || row >= height) throw new IndexOutOfBoundsException();
    }
    public boolean set(int col, int row, String text, boolean vertical) {
        int length = ExtendedUnicodeHelper.length(text);
        if (vertical) {
            if (col < 0 || col >= width) return false;
            boolean changed = false;
            int source = 0;
            for (int y = row; y < Math.min((long) row + length, height); y++) {
                if (y < 0) continue;
                int codePoint = text.codePointAt(source);
                changed |= buffer[y][col] != codePoint || color[y][col] != packed;
                setChar(buffer[y], color[y], col, codePoint);
                source = text.offsetByCodePoints(source, 1);
            }
            return changed;
        }
        if (row < 0 || row >= height) return false;
        boolean changed = false;
        int x = Math.max(col, 0), source = 0;
        for (int i = x; i < Math.min((long) col + length, width) && x < width; i++) {
            int codePoint = text.codePointAt(source);
            changed |= buffer[row][x] != codePoint || color[row][x] != packed;
            setChar(buffer[row], color[row], x, codePoint);
            x += Math.max(1, FontUtils.wcwidth(codePoint));
            source = text.offsetByCodePoints(source, 1);
        }
        return changed;
    }
    public boolean fill(int col, int row, int w, int h, int codePoint) {
        if (w <= 0 || h <= 0 || (long) col + w < 0 || (long) row + h < 0 || col >= width || row >= height) return false;
        boolean changed = false;
        for (int y = Math.max(0, row); y < Math.min((long) row + h, height); y++) {
            for (int x = Math.max(0, col); x < Math.min((long) col + w, width); x += Math.max(1, FontUtils.wcwidth(codePoint))) {
                changed |= buffer[y][x] != codePoint || color[y][x] != packed;
                setChar(buffer[y], color[y], x, codePoint);
            }
        }
        return changed;
    }
    public boolean copy(int col, int row, int w, int h, int tx, int ty) {
        if (w <= 0 || h <= 0 || tx == 0 && ty == 0) return false;
        int left = Math.clamp((long) col + tx, 0, width), right = Math.clamp((long) col + tx + w, 0, width);
        int top = Math.clamp((long) row + ty, 0, height), bottom = Math.clamp((long) row + ty + h, 0, height);
        boolean changed = false;
        int stepX = tx > 0 ? -1 : 1, stepY = ty > 0 ? -1 : 1;
        for (int y = stepY < 0 ? bottom - 1 : top; y >= top && y < bottom; y += stepY) {
            int fromY = y - ty;
            if (fromY < 0 || fromY >= height) continue;
            int[] sourceChars = buffer[fromY].clone();
            short[] sourceColors = color[fromY].clone();
            for (int x = stepX < 0 ? right - 1 : left; x >= left && x < right; x += stepX) {
                int fromX = x - tx;
                if (fromX < 0 || fromX >= width) continue;
                int cp = sourceChars[fromX];
                short packedColor = sourceColors[fromX];
                changed |= buffer[y][x] != cp || color[y][x] != packedColor;
                buffer[y][x] = cp;
                color[y][x] = packedColor;
                for (int offset = 1; offset < FontUtils.wcwidth(cp) && x + offset < width; offset++) {
                    buffer[y][x + offset] = ' ';
                    color[y][x + offset] = packedColor;
                }
            }
            if (left > 0 && FontUtils.wcwidth(buffer[y][left - 1]) > 1) {
                buffer[y][left - 1] = ' ';
                changed = true;
            }
        }
        return changed;
    }
    /** Coordinates in this method follow the original one-based bitblt protocol. */
    public boolean rawcopy(int col, int row, int w, int h, TextBuffer source, int fromCol, int fromRow) {
        boolean changed = false;
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int cp = source.buffer[fromRow + dy - 1][fromCol + dx - 1];
                short c = source.color[fromRow + dy - 1][fromCol + dx - 1];
                if (format.depth() != source.format.depth()) {
                    c = PackedColor.pack(new PackedColor.Color(PackedColor.unpackForeground(c, source.format)),
                            new PackedColor.Color(PackedColor.unpackBackground(c, source.format)), format);
                }
                int x = col + dx - 1, y = row + dy - 1;
                if (buffer[y][x] != cp || color[y][x] != c) {
                    changed = true;
                    buffer[y][x] = cp;
                    color[y][x] = c;
                }
            }
        }
        return changed;
    }
    private void setChar(int[] line, short[] lineColor, int x, int codePoint) {
        int width = FontUtils.wcwidth(codePoint);
        if (width > 1 && x >= line.length - 1) return;
        line[x] = codePoint;
        lineColor[x] = packed;
        for (int tail = x + 1; tail < x + width && tail < line.length; tail++) {
            line[tail] = ' ';
            lineColor[tail] = packed;
        }
        if (x > 0 && FontUtils.wcwidth(line[x - 1]) > 1) line[x - 1] = ' ';
    }
    @Override public void load(CompoundTag nbt) {
        int[][] resolutions = Settings.screenResolutionsByTier;
        int[] highest = resolutions[resolutions.length - 1];
        int maxResolution = Math.max(highest[0], highest[1]);
        int w = Math.clamp(nbt.getInt("width"), 1, maxResolution), h = Math.clamp(nbt.getInt("height"), 1, maxResolution);
        setSize(w, h);
        ListTag lines = nbt.getList("buffer", Tag.TAG_STRING);
        for (int y = 0; y < Math.min(h, lines.size()); y++) {
            PrimitiveIterator.OfInt values = lines.getString(y).codePoints().iterator();
            for (int x = 0; x < w && values.hasNext(); x++) buffer[y][x] = values.nextInt();
        }
        ColorDepth[] depths = ColorDepth.values();
        format = PackedColor.Depth.format(depths[Math.clamp(nbt.getInt("depth"), 0, depths.length - 1)]);
        format.load(nbt);
        setForeground(new PackedColor.Color(nbt.getInt("foreground"), nbt.getBoolean("foregroundIsPalette")));
        setBackground(new PackedColor.Color(nbt.getInt("background"), nbt.getBoolean("backgroundIsPalette")));
        if (nbt.contains("colors", Tag.TAG_BYTE_ARRAY)) {
            ByteBuffer bytes = ByteBuffer.wrap(nbt.getByteArray("colors"));
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                if (bytes.remaining() < Short.BYTES) return;
                color[y][x] = bytes.getShort();
            }
        } else if (nbt.contains("color", Tag.TAG_INT_ARRAY)) {
            int[] legacy = nbt.getIntArray("color");
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int index = x + y * w;
                if (index >= legacy.length) return;
                color[y][x] = (short) legacy[index];
            }
        }
    }
    @Override public void save(CompoundTag nbt) {
        nbt.putInt("width", width);
        nbt.putInt("height", height);
        ListTag lines = new ListTag();
        for (int y = 0; y < height; y++) lines.add(StringTag.valueOf(lineToString(y)));
        nbt.put("buffer", lines);
        nbt.putInt("depth", format.depth().ordinal());
        format.save(nbt);
        nbt.putInt("foreground", foreground.value());
        nbt.putBoolean("foregroundIsPalette", foreground.isPalette());
        nbt.putInt("background", background.value());
        nbt.putBoolean("backgroundIsPalette", background.isPalette());
        ByteBuffer bytes = ByteBuffer.allocate(Math.multiplyExact(Math.multiplyExact(width, height), Short.BYTES));
        for (short[] row : color) for (short value : row) bytes.putShort(value);
        nbt.putByteArray("colors", bytes.array());
    }
    public String lineToString(int y) {
        StringBuilder value = new StringBuilder();
        for (int cp : buffer[y]) value.appendCodePoint(cp);
        return value.toString();
    }
    @Override public String toString() {
        StringBuilder value = new StringBuilder();
        for (int y = 0; y < height; y++) {
            if (y > 0) value.append('\n');
            value.append(lineToString(y));
        }
        return value.toString();
    }
}
