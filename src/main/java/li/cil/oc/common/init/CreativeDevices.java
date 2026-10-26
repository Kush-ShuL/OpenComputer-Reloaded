package li.cil.oc.common.init;

import java.util.Arrays;
import java.util.List;
import li.cil.oc.Settings;
import li.cil.oc.common.item.HoverBootsItem;
import li.cil.oc.common.item.data.*;
import li.cil.oc.server.machine.luac.LuaStateFactory;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/** The original configured creative devices, with their original hardware layouts. */
public final class CreativeDevices {
    private CreativeDevices() {}
    private static ItemStack item(String name) {
        var descriptor = li.cil.oc.api.Items.get(name);
        return descriptor == null ? ItemStack.EMPTY : descriptor.createItemStack(1);
    }
    private static ItemStack[] parts(String... names) {
        return Arrays.stream(names).map(CreativeDevices::item).filter(stack -> !stack.isEmpty()).toArray(ItemStack[]::new);
    }
    private static ItemStack processor() { return LuaStateFactory.setDefaultArch(item("cpu3")); }
    private static ItemStack[] withProcessor(ItemStack[] items) {
        // CPU stacks must carry the configured architecture, just as the original creative factories did.
        var cpu = li.cil.oc.api.Items.get("cpu3");
        if (cpu != null) for (int index = 0; index < items.length; index++) if (items[index].is(cpu.item())) items[index] = processor();
        return items;
    }
    public static ItemStack drone(HolderLookup.Provider registries) {
        DroneData data = new DroneData();
        data.name = "Crecopter"; data.tier = 3; data.storedEnergy = (int) Settings.get().bufferDrone;
        data.components = withProcessor(parts("inventoryupgrade", "inventoryupgrade", "inventorycontrollerupgrade", "tankupgrade", "tankcontrollerupgrade", "leashupgrade", "angelupgrade", "wlancard2", "cpu3", "ram6", "ram6"));
        return data.createItemStack(registries);
    }
    public static ItemStack microcontroller(HolderLookup.Provider registries) {
        MicrocontrollerData data = new MicrocontrollerData();
        data.tier = 3; data.storedEnergy = (int) Settings.get().bufferMicrocontroller;
        data.components = withProcessor(parts("signupgrade", "pistonupgrade", "redstonecard2", "wlancard2", "cpu3", "ram6", "ram6"));
        return data.createItemStack(registries);
    }
    public static ItemStack robot(HolderLookup.Provider registries) {
        RobotData data = new RobotData();
        data.name = "Creatix"; data.tier = 3;
        data.robotEnergy = (int) Settings.get().bufferRobot; data.totalEnergy = data.robotEnergy;
        data.components = withProcessor(parts("screen1", "keyboard", "geolyzer", "inventoryupgrade", "inventoryupgrade", "inventoryupgrade", "inventoryupgrade", "inventorycontrollerupgrade", "tankupgrade", "tankcontrollerupgrade", "craftingupgrade", "hoverupgrade2", "angelupgrade", "tradingupgrade", "experienceupgrade", "graphicscard3", "redstonecard2", "wlancard2", "internetcard", "cpu3", "ram6", "ram6", "luabios", "openos", "hdd3"));
        data.containers = parts("cardcontainer3", "upgradecontainer3", "diskdrive");
        return data.createItemStack(registries);
    }
    public static ItemStack tablet(HolderLookup.Provider registries) {
        TabletData data = new TabletData();
        data.tier = 3; data.energy = Settings.get().bufferTablet; data.maxEnergy = data.energy;
        ItemStack[] components = withProcessor(parts("screen1", "keyboard", "signupgrade", "pistonupgrade", "geolyzer", "navigationupgrade", "analyzer", "graphicscard2", "redstonecard2", "wlancard2", "cpu3", "ram6", "ram6", "luabios", "hdd3"));
        System.arraycopy(components, 0, data.items, 0, components.length);
        data.items[31] = item("openos"); data.container = item("diskdrive");
        return data.createItemStack(registries);
    }
    public static ItemStack chargedBoots() {
        ItemStack stack = item("hoverboots");
        if (stack.getItem() instanceof HoverBootsItem boots) boots.setCharge(stack, Settings.get().bufferHoverBoots);
        return stack;
    }
    public static List<ItemStack> all(HolderLookup.Provider registries) {
        return List.of(drone(registries), microcontroller(registries), robot(registries), tablet(registries), chargedBoots());
    }
}
