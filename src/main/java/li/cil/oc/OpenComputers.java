package li.cil.oc;

import com.mojang.logging.LogUtils;
import li.cil.oc.api.API;
import li.cil.oc.common.recipe.Recipes;
import li.cil.oc.server.network.Network;
import li.cil.oc.server.network.WirelessNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Java entry point for the NeoForge migration. */
@Mod(OpenComputers.ID)
public final class OpenComputers {
    public static final String ID = "opencomputers";
    public static final String NAME = "OpenComputer-Reloaded";
    public static final String VERSION = "0.1.0-SNAPSHOT";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OpenComputers(IEventBus modBus, ModContainer container) {
        try { Settings.load(FMLPaths.CONFIGDIR.get().resolve("opencomputers/settings.conf")); }
        catch (IOException exception) { throw new UncheckedIOException("Could not load OpenComputers configuration", exception); }
        API.config = Settings.get().config;
        API.isPowerEnabled = !Settings.get().ignorePower;
        API.network = Network.INSTANCE;
        API.fileSystem = li.cil.oc.server.fs.FileSystem.INSTANCE;
        API.driver = li.cil.oc.server.driver.Registry.INSTANCE;
        API.machine = li.cil.oc.server.machine.MachineFactory.INSTANCE;
        API.items = li.cil.oc.common.init.Items.INSTANCE;
        API.nanomachines = li.cil.oc.common.nanomachines.Nanomachines.INSTANCE;
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT)
            li.cil.oc.client.ClientBootstrap.initialize();
        li.cil.oc.common.nanomachines.Nanomachines.registerBuiltins();
        NeoForge.EVENT_BUS.register(li.cil.oc.common.nanomachines.Nanomachines.class);
        modBus.addListener(li.cil.oc.common.network.ScreenNetworking::register);
        Recipes.register(modBus);
        li.cil.oc.common.init.Blocks.register(modBus);
        li.cil.oc.common.init.DiskDrives.register(modBus);
        li.cil.oc.common.init.PowerDevices.register(modBus);
        li.cil.oc.common.init.Adapters.register(modBus);
        li.cil.oc.common.init.AutomationDevices.register(modBus);
        li.cil.oc.common.init.RedstoneDevices.register(modBus);
        li.cil.oc.common.init.Holograms.register(modBus);
        li.cil.oc.common.capabilities.SimpleComponentBridge.register(modBus);
        li.cil.oc.common.IMC.register(modBus);
        NeoForge.EVENT_BUS.register(li.cil.oc.common.capabilities.SimpleComponentBridge.class);
        NeoForge.EVENT_BUS.register(li.cil.oc.common.CommonEvents.class);
        li.cil.oc.common.init.Items.register(modBus);
        li.cil.oc.common.menu.CaseMenu.register(modBus);
        NeoForge.EVENT_BUS.register(WirelessNetwork.class);
        NeoForge.EVENT_BUS.register(li.cil.oc.common.SaveHandler.class);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var factory = li.cil.oc.server.machine.MachineFactory.INSTANCE;
            if (li.cil.oc.server.machine.luac.LuaStateFactory.include53()) factory.add(li.cil.oc.server.machine.luac.NativeLua53Architecture.class);
            if (li.cil.oc.server.machine.luac.LuaStateFactory.include54()) factory.add(li.cil.oc.server.machine.luac.NativeLua54Architecture.class);
            if (li.cil.oc.server.machine.luac.LuaStateFactory.include52()) factory.add(li.cil.oc.server.machine.luac.NativeLua52Architecture.class);
            if (li.cil.oc.server.machine.luac.LuaStateFactory.includeLuaJ()) factory.add(li.cil.oc.server.machine.luaj.LuaJLuaArchitecture.class);
            li.cil.oc.api.Machine.LuaArchitecture = Settings.get().forceLuaJ ? li.cil.oc.server.machine.luaj.LuaJLuaArchitecture.class : factory.architectures().iterator().next();
            LOGGER.info("Available Lua architectures: {}; selected default: {}", factory.architectures().stream().map(factory::getArchitectureName).toList(), factory.getArchitectureName(li.cil.oc.api.Machine.LuaArchitecture));
            li.cil.oc.common.init.Blocks.initialize();
            li.cil.oc.common.init.Items.initializeDrivers();
            li.cil.oc.common.init.Adapters.initializeDrivers();
            li.cil.oc.common.init.RedstoneDevices.initializeDrivers();
            li.cil.oc.common.init.Converters.initialize();
            li.cil.oc.common.init.Items.INSTANCE.initializeLuaBios();
            li.cil.oc.common.init.Items.INSTANCE.initializeBundledLootDisks();
            Recipes.setLootRecraftingSupplier(() -> Settings.get().lootRecrafting);
            Recipes.setRecipeSetSupplier(() -> Settings.get().config.hasPath("recipes.profile") ? Settings.get().config.getString("recipes.profile") : "default");
        });
        LOGGER.info("{}: Java migration is in development; a complete playable port has not been released", NAME);
    }
}
