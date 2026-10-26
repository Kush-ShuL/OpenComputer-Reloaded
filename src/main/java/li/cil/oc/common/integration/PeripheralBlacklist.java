package li.cil.oc.common.integration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Actual retained CC peripheral class names for the optional integration driver. */
public final class PeripheralBlacklist {
    private static final Set<String> CLASSES = ConcurrentHashMap.newKeySet();
    private PeripheralBlacklist() {}
    public static void add(String name) { if (name == null || name.isBlank()) throw new IllegalArgumentException("Peripheral class name is empty"); CLASSES.add(name); }
    public static boolean contains(String name) { return CLASSES.contains(name); }
    public static boolean contains(Class<?> type) { return contains(type.getName()); }
    public static Set<String> classes() { return Set.copyOf(CLASSES); }
}
