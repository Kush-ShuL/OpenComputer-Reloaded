package li.cil.oc.client.integration.jade;

import li.cil.oc.Settings;
import net.neoforged.fml.ModList;

/** Optional-mod gate kept free of Jade and Minecraft client types. */
public final class JadeIntegration {
    private JadeIntegration() {}

    public static boolean enabled() {
        if (!ModList.get().isLoaded("jade")) return false;
        for (String entry : Settings.get().modBlacklist) {
            if ("jade".equalsIgnoreCase(entry.trim()) || "waila".equalsIgnoreCase(entry.trim())) return false;
        }
        return true;
    }
}
