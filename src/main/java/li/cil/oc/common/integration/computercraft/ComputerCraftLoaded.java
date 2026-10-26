package li.cil.oc.common.integration.computercraft;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import li.cil.oc.Settings;
import li.cil.oc.common.init.NetworkBlocks;
import li.cil.oc.common.integration.PeripheralBlacklist;
import li.cil.oc.common.redstone.BundledRedstone;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Loaded only after confirming the optional mod exists. */
final class ComputerCraftLoaded {
    private static boolean initialized;
    private ComputerCraftLoaded(){}
    static void register(IEventBus bus){bus.addListener(ComputerCraftLoaded::capabilities);}
    private static void capabilities(RegisterCapabilitiesEvent event){event.registerBlockEntity(PeripheralCapability.get(),NetworkBlocks.RELAY_TYPE.get(),(relay,side)->new RelayPeripheral(relay));}
    static synchronized void initialize(){
        if(initialized)return;initialized=true;
        if(Settings.get().config.hasPath("integration.peripheralBlacklist"))Settings.get().config.getStringList("integration.peripheralBlacklist").forEach(PeripheralBlacklist::add);
        li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverBlock)new PeripheralDriver());
        li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)new MediaDriver());
        li.cil.oc.api.Driver.add((li.cil.oc.api.driver.Converter)new LuaObjectConverter());
        BundledRedstone.addProvider(new ComputerCraftRedstone());
        ComputerCraftAPI.registerBundledRedstoneProvider(ComputerCraftRedstone::output);
    }
}
