package li.cil.oc.common.redstone;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;

/** Ordered, typed redstone integrations; the vanilla provider is a real provider. */
public final class BundledRedstone {
    public interface Provider {
        /** Called on the level's server thread, with the side pointing away from the host. */
        int computeInput(Level level, BlockPos position, Direction side);
        /** Return null when this integration has no bundled signal at this side. */
        int[] computeBundledInput(Level level, BlockPos position, Direction side);
        /** Push integrations can consume this; pull integrations read the host's state. */
        default void onOutputChanged(RedstoneHost host, Direction side, int[] output) {}
    }

    private static final CopyOnWriteArrayList<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final Provider VANILLA = new VanillaProvider();
    private BundledRedstone() {}
    public static void initializeVanilla() {
        if (li.cil.oc.common.integration.VanillaDrivers.integrationEnabled("minecraft")) addProvider(VANILLA);
    }
    public static void addProvider(Provider provider) { PROVIDERS.addIfAbsent(Objects.requireNonNull(provider)); }
    public static List<Provider> providers() { return List.copyOf(PROVIDERS); }
    public static boolean isAvailable() { return !PROVIDERS.isEmpty(); }
    private static boolean loaded(Level level, BlockPos position, Direction side) {
        return level != null && !level.isClientSide && level.hasChunkAt(position.relative(side));
    }
    public static int computeInput(Level level, BlockPos position, Direction side) {
        if (!loaded(level, position, side)) return 0;
        int result = 0;
        for (Provider provider : PROVIDERS) result = Math.max(result, provider.computeInput(level, position, side));
        return result;
    }
    public static int[] computeBundledInput(Level level, BlockPos position, Direction side) {
        if (!loaded(level, position, side)) return null;
        int[] result = null;
        for (Provider provider : PROVIDERS) {
            int[] values = provider.computeBundledInput(level, position, side);
            if (values == null) continue;
            if (result == null) result = new int[16];
            for (int color = 0; color < Math.min(16, values.length); color++) result[color] = Math.max(result[color], values[color]);
        }
        return result;
    }
    public static void outputChanged(RedstoneHost host, Direction side, int[] output) {
        if (host.world() == null || host.world().isClientSide) return;
        for (Provider provider : PROVIDERS) provider.onOutputChanged(host, side, output.clone());
    }
    /** Integration-facing output in world coordinates; disabled hosts emit no bundled power. */
    public static int[] output(Level level, BlockPos position, Direction side) {
        if (level == null || !level.hasChunkAt(position)) return new int[16];
        return level.getBlockEntity(position) instanceof RedstoneHost host && host.redstoneState().enabled()
                ? host.redstoneState().bundledOutput(side) : new int[16];
    }

    private static final class VanillaProvider implements Provider {
        @Override public int computeInput(Level level, BlockPos position, Direction side) {
            BlockPos neighbor = position.relative(side);
            int value = Math.max(level.getSignal(neighbor, side), level.getDirectSignal(neighbor, side));
            var state = level.getBlockState(neighbor);
            if (state.is(Blocks.REDSTONE_WIRE)) value = Math.max(value, state.getValue(RedStoneWireBlock.POWER));
            return value;
        }
        @Override public int[] computeBundledInput(Level level, BlockPos position, Direction side) { return null; }
    }
}
