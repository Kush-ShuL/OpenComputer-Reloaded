package li.cil.oc.common.template;

import java.util.*;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.driver.item.*;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.common.item.data.*;
import li.cil.oc.util.ItemUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Original robot/drone layouts and validation, backed by real portable-device data. */
public final class BuiltinAssemblyTemplates {
    private static final String PREFIX = BuiltinAssemblyTemplates.class.getName() + ".";
    private static boolean registered;
    private BuiltinAssemblyTemplates() {}
    public static synchronized void initialize() {
        if (registered) return;
        try {
            Class<? extends EnvironmentHost> robot = li.cil.oc.server.agent.RobotController.class;
            Class<? extends EnvironmentHost> drone = li.cil.oc.common.entity.DroneEntity.class;
            add("Robot (Tier 1)", "selectRobot1", "validateRobot", "assembleRobot", robot, new int[]{1,0,0}, new int[]{0,0,0}, new int[]{0,-1,-1,0,0,0,Integer.MAX_VALUE,0,-1});
            add("Robot (Tier 2)", "selectRobot2", "validateRobot", "assembleRobot", robot, new int[]{2,1,0}, new int[]{1,1,1,0,0,0}, new int[]{1,0,-1,1,1,1,Integer.MAX_VALUE,1,-1});
            add("Robot (Tier 3)", "selectRobot3", "validateRobot", "assembleRobot", robot, new int[]{2,1,1}, new int[]{2,2,2,1,1,1,0,0,0}, new int[]{2,1,1,2,2,2,Integer.MAX_VALUE,2,1});
            add("Robot (Creative)", "selectRobotCreative", "validateRobot", "assembleRobot", robot, new int[]{2,2,2}, new int[]{2,2,2,2,2,2,2,2,2}, new int[]{2,2,2,2,2,2,Integer.MAX_VALUE,2,2});
            add("Drone (Tier 1)", "selectDrone1", "validateDrone", "assembleDrone", drone, new int[0], new int[]{1,0}, new int[]{1,0,-1,0,0,-1,Integer.MAX_VALUE,-1,-1});
            add("Drone (Tier 2)", "selectDrone2", "validateDrone", "assembleDrone", drone, new int[0], new int[]{2,1,0}, new int[]{1,1,-1,0,0,0,Integer.MAX_VALUE,-1,-1});
            add("Drone (Creative)", "selectDroneCreative", "validateDrone", "assembleDrone", drone, new int[0], new int[]{2,2,2,2,2,2,2,2,2}, new int[]{2,2,2,2,2,2,Integer.MAX_VALUE,-1,-1});
            DisassemblerTemplates.add("Robot", stack -> named(stack, "robot"), (stack, ingredients, registries) -> {
                RobotData data = new RobotData(stack, registries);
                return recovered(join(new ItemStack[]{item(ItemUtils.caseNameWithTierSuffix("case", data.tier))}, data.containers, data.components));
            });
            DisassemblerTemplates.add("Drone", stack -> named(stack, "drone"), (stack, ingredients, registries) -> {
                DroneData data = new DroneData(stack, registries);
                return recovered(join(new ItemStack[]{item(ItemUtils.caseNameWithTierSuffix("dronecase", data.tier))}, data.components));
            });
            DisassemblerTemplates.add("Server", stack -> named(stack, "server1", "server2", "server3", "servercreative"), (stack, ingredients, registries) ->
                    new DisassemblerTemplates.Disassembly(Optional.of(ingredients), Optional.of(li.cil.oc.common.inventory.ServerInventory.loadItems(stack, registries).stream().filter(value -> !value.isEmpty()).map(ItemStack::copy).toArray(ItemStack[]::new))));
            AssemblerTemplates.addFilter(BuiltinAssemblyTemplates::allowedBase);
            registered = true;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not register the original built-in assembly templates", error); }
    }
    private static void add(String name, String select, String validate, String assemble, Class<?> host, int[] containers, int[] upgrades, int[] components) throws ReflectiveOperationException {
        CompoundTag data = new CompoundTag(); data.putString("name", name); data.putString("select", PREFIX + select); data.putString("validate", PREFIX + validate); data.putString("assemble", PREFIX + assemble); data.putString("hostClass", host.getName());
        data.put("containerSlots", tierSlots(containers)); data.put("upgradeSlots", tierSlots(upgrades));
        String[] kinds = {Slot.Card, Slot.Card, Slot.Card, Slot.CPU, Slot.Memory, Slot.Memory, "eeprom", Slot.HDD, Slot.HDD};
        ListTag slots = new ListTag();
        for (int index = 0; index < components.length; index++) { CompoundTag slot = new CompoundTag(); slot.putString("type", components[index] < 0 ? Slot.None : kinds[index]); slot.putInt("tier", components[index]); slots.add(slot); }
        data.put("componentSlots", slots); AssemblerTemplates.add(data);
    }
    private static ListTag tierSlots(int[] tiers) { ListTag list = new ListTag(); for (int tier : tiers) { CompoundTag data = new CompoundTag(); data.putInt("tier", tier); list.add(data); } return list; }
    private static boolean allowedBase(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (String value : Settings.get().config.getStringList("misc.assemblerBlacklist")) {
            String[] parts = value.toLowerCase(Locale.ROOT).split("@", 2);
            if (!id.equals(parts[0])) continue;
            if (parts.length == 1) return false;
            try { if (stack.getDamageValue() == Integer.parseInt(parts[1])) return false; } catch (NumberFormatException ignored) {}
        }
        return true;
    }
    private static boolean named(ItemStack stack, String... names) { var item = li.cil.oc.api.Items.get(stack); return item != null && Arrays.asList(names).contains(item.name()); }
    private static ItemStack item(String name) { var descriptor = li.cil.oc.api.Items.get(name); return descriptor == null ? ItemStack.EMPTY : descriptor.createItemStack(1); }
    public static boolean selectRobot1(ItemStack stack) { return named(stack, "case1"); }
    public static boolean selectRobot2(ItemStack stack) { return named(stack, "case2"); }
    public static boolean selectRobot3(ItemStack stack) { return named(stack, "case3"); }
    public static boolean selectRobotCreative(ItemStack stack) { return named(stack, "casecreative"); }
    public static boolean selectDrone1(ItemStack stack) { return named(stack, "dronecase1"); }
    public static boolean selectDrone2(ItemStack stack) { return named(stack, "dronecase2"); }
    public static boolean selectDroneCreative(ItemStack stack) { return named(stack, "dronecasecreative"); }
    public static Object[] validateRobot(Container inventory) { return validate(inventory, li.cil.oc.server.agent.RobotController.class, false); }
    public static Object[] validateDrone(Container inventory) { return validate(inventory, li.cil.oc.common.entity.DroneEntity.class, true); }
    private static Object[] validate(Container inventory, Class<? extends EnvironmentHost> host, boolean drone) {
        boolean cpu = false, ram = false, requiresRam = true;
        for (int slot = 1; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot); DriverItem driver = Driver.driverFor(stack, host);
            if (driver instanceof Processor processor) { cpu = true; Class<? extends Architecture> architecture = processor.architecture(stack); if (architecture != null && architecture.getAnnotation(Architecture.NoMemoryRequirements.class) != null) requiresRam = false; }
            if (driver instanceof Memory) ram = true;
        }
        int tier = ItemUtils.caseTier(inventory.getItem(0)), complexity = complexity(inventory, host);
        int maximum = drone ? tier == 1 ? 8 : tier == 3 ? 9001 : 5 : maxComplexity(inventory, host, tier);
        boolean valid = tier >= 0 && cpu && (ram || !requiresRam) && complexity <= maximum;
        Component message = !cpu ? Component.translatable("oc:gui.Assembler.InsertCPU") : !ram && requiresRam ? Component.translatable("oc:gui.Assembler.InsertRAM") : Component.translatable("oc:gui.Assembler.Complexity", complexity, maximum);
        List<Component> warnings = new ArrayList<>();
        warn(warnings, inventory, "BIOS", stack -> named(stack, "eeprom"));
        if (!drone) {
            warn(warnings, inventory, "Screen", stack -> named(stack, "screen1"));
            warn(warnings, inventory, "Keyboard", stack -> named(stack, "keyboard"));
            warn(warnings, inventory, "GraphicsCard", stack -> named(stack, "apu1", "apu2", "apucreative", "graphicscard1", "graphicscard2", "graphicscard3"));
            warn(warnings, inventory, "Inventory", stack -> Driver.driverFor(stack, host) instanceof li.cil.oc.api.driver.item.Inventory);
            warn(warnings, inventory, "OS", stack -> { var driver = Driver.driverFor(stack, host); return driver != null && (Slot.Floppy.equals(driver.slot(stack)) || Slot.HDD.equals(driver.slot(stack))); });
        }
        if (!warnings.isEmpty()) warnings.addFirst(Component.translatable("oc:gui.Assembler.Warnings"));
        return new Object[]{valid, message, warnings.toArray(Component[]::new)};
    }
    private static void warn(List<Component> warnings, Container inventory, String name, java.util.function.Predicate<ItemStack> predicate) { for (int slot = 1; slot < inventory.getContainerSize(); slot++) if (!inventory.getItem(slot).isEmpty() && predicate.test(inventory.getItem(slot))) return; warnings.add(Component.translatable("oc:gui.Assembler.Warning." + name)); }
    private static int complexity(Container inventory, Class<? extends EnvironmentHost> host) {
        int value = 0;
        for (int slot = 1; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot); var driver = Driver.driverFor(stack, host);
            if (driver == null || driver instanceof Processor || "eeprom".equals(driver.slot(stack))) continue;
            value += (1 + driver.tier(stack)) * (driver instanceof li.cil.oc.api.driver.item.Container ? 2 : 1);
        }
        return value;
    }
    private static int maxComplexity(Container inventory, Class<? extends EnvironmentHost> host, int tier) {
        if (tier < 0 || tier >= Settings.deviceComplexityByTier.length) return 0;
        int cpuTier = 0;
        for (int slot = 1; slot < inventory.getContainerSize(); slot++) { ItemStack stack = inventory.getItem(slot); var driver = Driver.driverFor(stack, host); if (driver instanceof Processor) cpuTier += driver.tier(stack); }
        return Settings.deviceComplexityByTier[tier] - (Math.min(2, tier) - cpuTier) * 6;
    }
    private static HolderLookup.Provider registries(Container inventory) { if (inventory instanceof BlockEntity entity && entity.getLevel() != null) return entity.getLevel().registryAccess(); throw new IllegalStateException("Assembly requires the inventory's world registry access"); }
    public static Object[] assembleRobot(Container inventory) {
        RobotData data = new RobotData(); data.tier = ItemUtils.caseTier(inventory.getItem(0)); data.name = RobotData.randomName(); data.robotEnergy = (int) Settings.get().bufferRobot; data.totalEnergy = data.robotEnergy;
        data.containers = copySlots(inventory, 1, 4); data.components = copySlots(inventory, 4, inventory.getContainerSize());
        return new Object[]{data.createItemStack(registries(inventory)), Settings.get().robotBaseCost + complexity(inventory, li.cil.oc.server.agent.RobotController.class) * Settings.get().robotComplexityCost};
    }
    public static Object[] assembleDrone(Container inventory) {
        DroneData data = new DroneData(); data.tier = ItemUtils.caseTier(inventory.getItem(0)); data.name = RobotData.randomName(); data.storedEnergy = (int) Settings.get().bufferDrone; data.components = copySlots(inventory, 1, inventory.getContainerSize());
        return new Object[]{data.createItemStack(registries(inventory)), Settings.get().droneBaseCost + complexity(inventory, li.cil.oc.common.entity.DroneEntity.class) * Settings.get().droneComplexityCost};
    }
    private static ItemStack[] copySlots(Container inventory, int start, int end) { List<ItemStack> stacks = new ArrayList<>(); for (int slot = start; slot < end; slot++) if (!inventory.getItem(slot).isEmpty()) stacks.add(inventory.getItem(slot).copy()); return stacks.toArray(ItemStack[]::new); }
    private static ItemStack[] join(ItemStack[]... parts) { return Arrays.stream(parts).flatMap(Arrays::stream).filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toArray(ItemStack[]::new); }
    private static DisassemblerTemplates.Disassembly recovered(ItemStack[] stacks) { return new DisassemblerTemplates.Disassembly(Optional.of(stacks), Optional.empty()); }
}
