package li.cil.oc.common.integration.computercraft;

import li.cil.oc.Settings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/** Safe optional entry point: no ComputerCraft types occur in its signatures or fields. */
public final class ComputerCraftIntegration {
    private ComputerCraftIntegration(){}
    private static boolean enabledInConfiguration(){
        var config=Settings.get().config;
        if(!config.hasPath("integration.computerCraft.enabled")||!config.getBoolean("integration.computerCraft.enabled"))return false;
        return !config.hasPath("integration.modBlacklist")||config.getStringList("integration.modBlacklist").stream().noneMatch(name->name.trim().equalsIgnoreCase("computercraft"));
    }
    public static boolean available(){return enabledInConfiguration()&&ModList.get().isLoaded("computercraft");}
    public static void register(IEventBus bus){if(available())ComputerCraftLoaded.register(bus);}
    public static void initializeDrivers(){if(available())ComputerCraftLoaded.initialize();}
}
