package li.cil.oc.client.integration.jei;

import li.cil.oc.Settings;

/** Client lifecycle bridge which can be linked even when JEI is absent. */
public final class JeiIntegration {
    private static Runnable refresh;

    private JeiIntegration() {}

    static boolean enabled() {
        return Settings.get().modBlacklist.stream().noneMatch(id -> id.trim().equalsIgnoreCase("jei"));
    }

    static void attach(Runnable action) { refresh = action; }

    /** Call after the client program-disk snapshot changes, on the Minecraft thread. */
    public static void refreshLoot() {
        Runnable action = refresh;
        if (action != null && enabled()) action.run();
    }
}
