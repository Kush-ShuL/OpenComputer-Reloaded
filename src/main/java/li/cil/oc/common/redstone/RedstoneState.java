package li.cil.oc.common.redstone;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Original vanilla, sixteen-channel bundled and RedNet state, sampled on the server thread. */
public final class RedstoneState {
    private final RedstoneHost host;
    private final Consumer<RedstoneChange> changed;
    private volatile int[] input = {-1, -1, -1, -1, -1, -1};
    private volatile int[] output = new int[6];
    private volatile int[][] bundledInput = channels(-1), rednetInput = channels(-1), bundledOutput = channels(0);
    private volatile boolean enabled;
    private volatile boolean inputDirty = true;

    public RedstoneState(RedstoneHost host, Consumer<RedstoneChange> changed) {
        this.host = host; this.changed = changed;
    }
    private BlockPos position() { return BlockPos.containing(host.xPosition(), host.yPosition(), host.zPosition()); }
    public boolean enabled() { return enabled; }
    public int input(Direction side) { return Math.max(0, input[side.get3DDataValue()]); }
    public int output(Direction side) { return output[host.toLocal(side).get3DDataValue()]; }
    public int[] inputs() { int[] values = input.clone(); for (int i = 0; i < values.length; i++) values[i] = Math.max(0, values[i]); return values; }
    public int[] outputs() { return Arrays.stream(Direction.values()).mapToInt(this::output).toArray(); }
    public int bundledInput(Direction side, int color) {
        int index = side.get3DDataValue(); checkColor(color);
        return Math.max(0, Math.max(bundledInput[index][color], rednetInput[index][color]));
    }
    public int[] bundledInput(Direction side) {
        int[] values = new int[16];
        for (int color = 0; color < 16; color++) values[color] = bundledInput(side, color);
        return values;
    }
    public int[][] bundledInputs() {
        int[][] values = new int[6][];
        for (Direction side : Direction.values()) values[side.get3DDataValue()] = bundledInput(side);
        return values;
    }
    /** Raw input rows used by the pinned no-side output/assignment return values. */
    public int[][] bundledInputSnapshot() { return Arrays.stream(bundledInput).map(int[]::clone).toArray(int[][]::new); }
    public int bundledOutput(Direction side, int color) { checkColor(color); return bundledOutput[host.toLocal(side).get3DDataValue()][color]; }
    public int[] bundledOutput(Direction side) { return bundledOutput[host.toLocal(side).get3DDataValue()].clone(); }
    public int[][] bundledOutputs() {
        int[][] values = new int[6][];
        for (Direction side : Direction.values()) values[side.get3DDataValue()] = bundledOutput(side);
        return values;
    }
    public void rescan() { inputDirty = true; }

    public void setEnabled(boolean value) {
        requireServerThread();
        if (enabled == value) return;
        enabled = value;
        if (!value) { output = new int[6]; bundledOutput = channels(0); }
        Level level = host.world();
        if (level != null && !level.isClientSide) {
            BlockPos pos = position();
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            for (Direction side : Direction.values()) BundledRedstone.outputChanged(host, side, value ? bundledOutput(side) : new int[16]);
            publish();
        }
    }

    public boolean setOutput(Direction side, int value) {
        requireServerThread();
        if (output(side) == value) return false;
        int[] values = output.clone();
        values[host.toLocal(side).get3DDataValue()] = value;
        output = values;
        outputChanged(side);
        return true;
    }

    public boolean setOutput(Map<?, ?> values) {
        boolean modified = false;
        for (Direction global : Direction.values()) {
            int side = host.toLocal(global).get3DDataValue();
            Object value = fuzzy(values, side);
            if (value instanceof Number number) modified |= setOutput(global, number.intValue());
        }
        return modified;
    }

    public boolean setBundledOutput(Direction side, int color, int value) {
        requireServerThread();
        checkColor(color);
        if (bundledOutput(side, color) == value) return false;
        int[][] next = bundledOutput.clone();
        int index = host.toLocal(side).get3DDataValue();
        next[index] = next[index].clone(); next[index][color] = value; bundledOutput = next;
        outputChanged(side); return true;
    }
    public boolean setBundledOutput(Direction side, Map<?, ?> values) {
        requireServerThread();
        int[][] next = bundledOutput.clone(); int index = host.toLocal(side).get3DDataValue();
        int[] row = next[index].clone(); boolean modified = false;
        for (int color = 0; color < 16; color++) {
            Object value = fuzzy(values, color);
            if (value instanceof Number number && row[color] != number.intValue()) { row[color] = number.intValue(); modified = true; }
        }
        if (modified) { next[index] = row; bundledOutput = next; outputChanged(side); }
        return modified;
    }
    public boolean setBundledOutput(Map<?, ?> values) {
        boolean modified = false;
        for (Direction side : Direction.values()) {
            Object value = fuzzy(values, host.toLocal(side).get3DDataValue());
            if (value instanceof Map<?, ?> colors) modified |= setBundledOutput(side, colors);
        }
        return modified;
    }
    public void setBundledInput(Direction side, int color, int value) { updateChannel(side, color, value, false); }
    public void setRednetInput(Direction side, int color, int value) { updateChannel(side, color, value, true); }
    public void setBundledInput(Direction side, int[] values) {
        for (int color = 0; color < 16; color++) setBundledInput(side, color, values == null || color >= values.length ? 0 : values[color]);
    }
    private void updateChannel(Direction side, int color, int value, boolean rednet) {
        requireServerThread();
        checkColor(color); int index = side.get3DDataValue();
        int[][] previous = rednet ? rednetInput : bundledInput;
        int old = previous[index][color];
        if (old == value) return;
        int[][] next = previous.clone(); next[index] = previous[index].clone(); next[index][color] = value;
        if (rednet) rednetInput = next; else bundledInput = next;
        if (old >= 0) changed.accept(new RedstoneChange(host.toLocal(side), old, value, color));
    }
    private void outputChanged(Direction side) {
        Level level = host.world();
        if (level != null && !level.isClientSide) {
            BlockPos pos = position(), neighbor = pos.relative(side);
            level.neighborChanged(neighbor, level.getBlockState(pos).getBlock(), pos);
            level.updateNeighborsAtExceptFromFacing(neighbor, level.getBlockState(neighbor).getBlock(), side.getOpposite());
            BundledRedstone.outputChanged(host, side, enabled ? bundledOutput(side) : new int[16]);
            publish();
        }
    }

    /** Runs on the server thread, so direct Lua reads only observe a cache. */
    public void tick() {
        Level level = host.world();
        if (!inputDirty || level == null || level.isClientSide) return;
        inputDirty = false;
        BlockPos pos = position();
        int[] old = input, next = old.clone();
        for (Direction side : Direction.values()) {
            next[side.get3DDataValue()] = BundledRedstone.computeInput(level, pos, side);
        }
        input = next;
        for (Direction side : Direction.values()) {
            int index = side.get3DDataValue();
            if (old[index] >= 0 && old[index] != next[index])
                changed.accept(new RedstoneChange(host.toLocal(side), old[index], next[index]));
            setBundledInput(side, BundledRedstone.computeBundledInput(level, pos, side));
        }
    }

    private void publish() {
        host.markChanged();
        Level level = host.world();
        BlockPos pos = position();
        var state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 2);
    }
    public void load(CompoundTag tag) {
        if (tag.contains("oc:rs.input")) input = six(tag.getIntArray("oc:rs.input"), -1);
        if (tag.contains("oc:rs.output")) output = six(tag.getIntArray("oc:rs.output"), 0);
        if (tag.contains("oc:rs.bundledInput")) bundledInput = readChannels(tag.getList("oc:rs.bundledInput", Tag.TAG_INT_ARRAY), -1);
        if (tag.contains("oc:rs.rednetInput")) rednetInput = readChannels(tag.getList("oc:rs.rednetInput", Tag.TAG_INT_ARRAY), -1);
        if (tag.contains("oc:rs.bundledOutput")) bundledOutput = readChannels(tag.getList("oc:rs.bundledOutput", Tag.TAG_INT_ARRAY), 0);
        inputDirty = true;
    }
    public void save(CompoundTag tag) {
        tag.putIntArray("oc:rs.input", input); tag.putIntArray("oc:rs.output", output);
        tag.put("oc:rs.bundledInput", writeChannels(bundledInput)); tag.put("oc:rs.rednetInput", writeChannels(rednetInput));
        tag.put("oc:rs.bundledOutput", writeChannels(bundledOutput));
    }
    public void loadClient(CompoundTag tag) { enabled = tag.getBoolean("isOutputEnabled"); output = six(tag.getIntArray("output"), 0); }
    public void saveClient(CompoundTag tag) { tag.putBoolean("isOutputEnabled", enabled); tag.putIntArray("output", output); }
    private static int[] six(int[] source, int defaultValue) {
        int[] values = new int[6]; Arrays.fill(values, defaultValue);
        System.arraycopy(source, 0, values, 0, Math.min(6, source.length)); return values;
    }
    private static Object fuzzy(Map<?, ?> map, int key) { return map.containsKey(key) ? map.get(key) : map.get((double) key); }
    private void requireServerThread() {
        if (host.world() instanceof ServerLevel level && !level.getServer().isSameThread())
            throw new IllegalStateException("Redstone mutations must run on the server thread");
    }
    private static void checkColor(int color) { if (color < 0 || color > 15) throw new IllegalArgumentException("invalid color"); }
    private static int[][] channels(int defaultValue) {
        int[][] values = new int[6][16];
        if (defaultValue != 0) for (int[] row : values) Arrays.fill(row, defaultValue);
        return values;
    }
    private static int[][] readChannels(ListTag source, int defaultValue) {
        int[][] values = channels(defaultValue);
        for (int side = 0; side < Math.min(6, source.size()); side++) {
            int[] row = ((IntArrayTag) source.get(side)).getAsIntArray();
            System.arraycopy(row, 0, values[side], 0, Math.min(16, row.length));
        }
        return values;
    }
    private static ListTag writeChannels(int[][] source) {
        ListTag values = new ListTag();
        for (int[] row : source) values.add(new IntArrayTag(row.clone()));
        return values;
    }
}
