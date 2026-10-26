package li.cil.oc.server.machine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/** Original architecture-filtered program-to-disk hints, with real disk resolution. */
public final class ProgramLocations {
    private static final Map<String, Map<String, String>> ARCHITECTURES = new LinkedHashMap<>();
    private static final Map<String, String> GLOBAL = new LinkedHashMap<>();
    private static boolean initialized;
    private ProgramLocations() {}
    public static synchronized void addMapping(String program, String label, String... architectures) {
        if (program == null || program.isBlank() || label == null || label.isBlank()) throw new IllegalArgumentException("Program name and disk label must not be empty");
        if (architectures == null || architectures.length == 0) GLOBAL.put(program, label);
        else for (String architecture : architectures) { if (architecture == null || architecture.isBlank()) throw new IllegalArgumentException("Architecture name is empty"); ARCHITECTURES.computeIfAbsent(architecture, ignored -> new LinkedHashMap<>()).put(program, label); }
    }
    public static synchronized Map<String, String> getMappings(String architecture) { Map<String, String> mappings = new LinkedHashMap<>(ARCHITECTURES.getOrDefault(architecture, Map.of())); mappings.putAll(GLOBAL); return java.util.Collections.unmodifiableMap(mappings); }
    public static List<ItemStack> disksForProgram(String program, String architecture) { String label = getMappings(architecture).get(program); return label == null ? List.of() : li.cil.oc.common.init.Items.INSTANCE.programDisks(label); }
    public static synchronized void initializeBundled() {
        if (initialized) return;
        Map<String, String> mappings = Map.ofEntries(Map.entry("build", "builder"), Map.entry("dig", "dig"), Map.entry("base64", "data"), Map.entry("deflate", "data"), Map.entry("gpg", "data"), Map.entry("inflate", "data"), Map.entry("md5sum", "data"), Map.entry("sha256sum", "data"), Map.entry("refuel", "generator"), Map.entry("irc", "irc"), Map.entry("maze", "maze"), Map.entry("arp", "network"), Map.entry("ifconfig", "network"), Map.entry("ping", "network"), Map.entry("route", "network"), Map.entry("opl-flash", "openloader"), Map.entry("oppm", "oppm"));
        mappings.forEach((program, label) -> addMapping(program, label, "Lua 5.2", "Lua 5.3", "LuaJ"));
        initialized = true;
    }
}
