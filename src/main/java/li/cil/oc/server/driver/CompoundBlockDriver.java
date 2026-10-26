package li.cil.oc.server.driver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;

/** Exposes all compatible block drivers as one stable component. */
public final class CompoundBlockDriver implements DriverBlock {
    public final List<DriverBlock> sidedBlocks;

    public CompoundBlockDriver(List<DriverBlock> drivers) { sidedBlocks = List.copyOf(drivers); }
    public CompoundBlockDriver(DriverBlock... drivers) { this(List.of(drivers)); }

    @Override
    public CompoundBlockEnvironment createEnvironment(Level level, BlockPos pos, Direction side) {
        List<CompoundBlockEnvironment.Entry> entries = new ArrayList<>();
        for (DriverBlock driver : sidedBlocks) {
            ManagedEnvironment environment = driver.createEnvironment(level, pos, side);
            if (environment != null) entries.add(new CompoundBlockEnvironment.Entry(driver.getClass().getName(), environment));
        }
        return entries.isEmpty() ? null : new CompoundBlockEnvironment(cleanName(name(level, pos, entries)), entries);
    }

    @Override
    public boolean worksWith(Level level, BlockPos pos, Direction side) {
        return sidedBlocks.stream().allMatch(driver -> driver.worksWith(level, pos, side));
    }

    private static String name(Level level, BlockPos pos, List<CompoundBlockEnvironment.Entry> entries) {
        NamedBlock preferred = null;
        for (CompoundBlockEnvironment.Entry entry : entries)
            if (entry.environment() instanceof NamedBlock named && (preferred == null || named.priority() >= preferred.priority())) preferred = named;
        if (preferred != null) return preferred.preferredName();
        if (level.getBlockEntity(pos) instanceof Nameable named) return named.getName().getString().replaceFirst("^container\\.", "");
        var block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (block != null) return block.getPath();
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
            if (type != null) return type.getPath();
        }
        return "component";
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) return "component";
        String result = name.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(Locale.ROOT);
        if (!Character.isLetter(result.charAt(0)) && result.charAt(0) != '_') result = '_' + result;
        return result;
    }

    @Override public boolean equals(Object other) {
        return other instanceof CompoundBlockDriver driver && sidedBlocks.size() == driver.sidedBlocks.size()
                && new HashSet<>(sidedBlocks).equals(new HashSet<>(driver.sidedBlocks));
    }
    @Override public int hashCode() { return new HashSet<>(sidedBlocks).hashCode(); }
}
