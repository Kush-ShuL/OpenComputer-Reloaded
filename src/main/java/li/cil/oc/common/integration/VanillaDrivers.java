package li.cil.oc.common.integration;

import li.cil.oc.Settings;
import li.cil.oc.api.driver.*;
import li.cil.oc.server.driver.block.*;

/** Remaining vanilla special-purpose adapters; generic capability drivers are registered elsewhere. */
public final class VanillaDrivers {
    private VanillaDrivers(){}
    public static boolean integrationEnabled(String... identifiers){for(String excluded:Settings.get().modBlacklist)for(String identifier:identifiers)if(excluded.trim().equalsIgnoreCase(identifier))return false;return true;}
    private static void add(DriverBlock driver){li.cil.oc.api.Driver.add(driver);if(driver instanceof EnvironmentProvider provider)li.cil.oc.api.Driver.add(provider);}
    public static void initializeDrivers(){if(!integrationEnabled("minecraft"))return;add(new Beacon());add(new BrewingStand());add(new Comparator());add(new Furnace());add(new MobSpawner());add(new NoteBlock());add(new RecordPlayer());if(Settings.get().enableCommandBlockDriver)add(new CommandBlock());}
}
