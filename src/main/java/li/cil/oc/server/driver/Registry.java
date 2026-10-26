package li.cil.oc.server.driver;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import li.cil.oc.api.detail.DriverAPI;
import li.cil.oc.api.driver.Converter;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.InventoryProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.server.driver.converter.MissingRegistryContextException;
import net.minecraft.core.HolderLookup;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.EnvironmentHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ordered, init-phase driver registration and architecture-safe value conversion. */
public final class Registry implements DriverAPI {
    public static final Registry INSTANCE = new Registry();
    private static final Logger LOGGER = LoggerFactory.getLogger(Registry.class);
    private final CopyOnWriteArrayList<DriverBlock> blocks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DriverItem> items = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Converter> converters = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<EnvironmentProvider> environments = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<InventoryProvider> inventories = new CopyOnWriteArrayList<>();
    private final Map<Item, Set<Class<?>>> blacklist = new IdentityHashMap<>();
    public volatile boolean locked;

    public Registry() {}

    public void lock() { locked = true; }

    private <T> void add(CopyOnWriteArrayList<T> target, T entry) {
        if (locked) throw new IllegalStateException("Please register OpenComputers drivers and converters during common setup");
        Objects.requireNonNull(entry, "OpenComputers driver");
        if (target.addIfAbsent(entry)) LOGGER.debug("Registering OpenComputers driver {}", entry.getClass().getName());
    }

    @Override public void add(DriverBlock driver) { add(blocks, driver); }
    @Override public void add(DriverItem driver) { add(items, driver); }
    @Override public void add(Converter converter) { add(converters, converter); }
    @Override public void add(EnvironmentProvider provider) { add(environments, provider); }
    @Override public void add(InventoryProvider provider) { add(inventories, provider); }

    @Override
    public DriverBlock driverFor(Level level, BlockPos pos, Direction side) {
        List<DriverBlock> supported = new ArrayList<>();
        for (DriverBlock driver : blocks) if (driver.worksWith(level, pos, side)) supported.add(driver);
        return supported.isEmpty() ? null : new CompoundBlockDriver(supported);
    }

    @Override
    public DriverItem driverFor(ItemStack stack, Class<? extends EnvironmentHost> host) {
        if (stack == null || stack.isEmpty() || isBlacklistedHost(stack, host)) return null;
        // A matching host-aware driver takes precedence over generic drivers,
        // including when all matching host-aware drivers reject this host.
        boolean hasHostAware = false;
        for (DriverItem driver : items) {
            if (driver instanceof HostAware aware && driver.worksWith(stack)) {
                hasHostAware = true;
                if (aware.worksWith(stack, host)) return driver;
            }
        }
        return hasHostAware ? null : driverFor(stack);
    }

    @Override
    public DriverItem driverFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (DriverItem driver : items) if (driver.worksWith(stack)) return driver;
        return null;
    }

    @Override @Deprecated
    public Class<?> environmentFor(ItemStack stack) {
        for (EnvironmentProvider provider : environments) {
            Class<?> environment = provider.getEnvironment(stack);
            if (environment != null) return environment;
        }
        return null;
    }

    @Override
    public Set<Class<?>> environmentsFor(ItemStack stack) {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (EnvironmentProvider provider : environments) {
            Class<?> environment = provider.getEnvironment(stack);
            if (environment != null) result.add(environment);
        }
        return Set.copyOf(result);
    }

    @Override
    public IItemHandler itemHandlerFor(ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty()) return null;
        for (InventoryProvider provider : inventories) {
            if (provider.worksWith(stack, player)) {
                Container inventory = provider.getInventory(stack, player);
                if (inventory != null) return new InvWrapper(inventory);
            }
        }
        return stack.getCapability(Capabilities.ItemHandler.ITEM);
    }

    @Override public Collection<DriverItem> itemDrivers() { return List.copyOf(items); }

    public synchronized void blacklistHost(ItemStack stack, Class<?> host) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Cannot blacklist an empty component stack");
        blacklist.computeIfAbsent(stack.getItem(), ignored -> new LinkedHashSet<>()).add(Objects.requireNonNull(host));
    }

    public synchronized boolean isBlacklistedHost(ItemStack stack, Class<?> host) {
        if (host == null || stack == null || stack.isEmpty()) return false;
        for (Class<?> excluded : blacklist.getOrDefault(stack.getItem(), Set.of()))
            if (excluded.isAssignableFrom(host)) return true;
        return false;
    }

    /** Convert a callback result, retaining cycles and shared references. */
    public static Object[] convert(Object[] values) {
        return convert(values, null);
    }

    /** Use the invoking machine's lookup throughout the complete object graph. */
    public static Object[] convert(Object[] values, HolderLookup.Provider registries) {
        if (values == null) return null;
        Object[] result = new Object[values.length];
        IdentityHashMap<Object, Object> memo = new IdentityHashMap<>();
        memo.put(values, result);
        for (int i = 0; i < values.length; i++) result[i] = convertRecursively(values[i], memo, registries);
        return result;
    }

    public static Object convertRecursively(Object value, IdentityHashMap<Object, Object> memo) {
        return convertRecursively(value, memo, null);
    }

    private static Object convertRecursively(Object value, IdentityHashMap<Object, Object> memo, HolderLookup.Provider registries) {
        if (value == null) return null;
        if (memo.containsKey(value)) return memo.get(value);
        if (value instanceof Boolean || value instanceof Character || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof String || value instanceof Value) return value;
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof Optional<?> optional) return convertRecursively(optional.orElse(null), memo, registries);
        Class<?> type = value.getClass();
        if (type.isArray()) {
            if (type.componentType().isPrimitive() || type.componentType() == String.class) return value;
            Object[] converted = new Object[Array.getLength(value)];
            memo.put(value, converted);
            for (int i = 0; i < converted.length; i++) converted[i] = convertRecursively(Array.get(value, i), memo, registries);
            return converted;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> converted = new LinkedHashMap<>();
            memo.put(value, converted);
            fillMap(map, converted, memo, registries);
            return converted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> source = new ArrayList<>();
            for (Object entry : iterable) source.add(entry);
            Object[] converted = new Object[source.size()];
            memo.put(value, converted);
            for (int i = 0; i < converted.length; i++) converted[i] = convertRecursively(source.get(i), memo, registries);
            return converted;
        }
        Map<Object, Object> converted = new LinkedHashMap<>();
        memo.put(value, converted);
        for (Converter converter : INSTANCE.converters) {
            try { converter.convert(value, converted, registries); }
            catch (MissingRegistryContextException error) { throw error; }
            catch (RuntimeException error) { LOGGER.warn("An OpenComputers type converter threw an exception", error); }
        }
        if (converted.isEmpty()) {
            String text = value.toString();
            memo.put(value, text);
            return text;
        }
        // Converters may put the original object back into their output map.
        // Convert in place so references already memoized to it remain valid.
        Map<Object, Object> source = new LinkedHashMap<>(converted);
        converted.clear();
        memo.put(converted, converted);
        fillMap(source, converted, memo, registries);
        memo.remove(converted);
        if (converted.size() == 1 && converted.containsKey("oc:flatten")) {
            Object flattened = converted.get("oc:flatten");
            memo.put(value, flattened);
            return flattened;
        }
        return converted;
    }

    private static void fillMap(Map<?, ?> source, Map<Object, Object> destination, IdentityHashMap<Object, Object> memo, HolderLookup.Provider registries) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null)
                destination.put(convertRecursively(entry.getKey(), memo, registries), convertRecursively(entry.getValue(), memo, registries));
        }
    }
}
