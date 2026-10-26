package li.cil.oc.common.item.data;

import li.cil.oc.Constants;
import li.cil.oc.OpenComputers;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Items;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RobotData extends ItemData {
    private static final List<String> NAMES = loadNames();
    public String name = "";
    public int totalEnergy, robotEnergy, tier;
    public ItemStack[] components = {}, containers = {};
    public int lightColor = 0xF23030;
    public RobotData() { super(Constants.BlockName.Robot); }
    public RobotData(ItemStack stack, HolderLookup.Provider registries) { this(); load(stack, registries); }
    public static String randomName() { return NAMES.isEmpty() ? "Robot" : NAMES.get(ThreadLocalRandom.current().nextInt(NAMES.size())); }
    private static List<String> loadNames() {
        try (InputStream stream = RobotData.class.getResourceAsStream("/assets/opencomputers/robot.names")) {
            if (stream == null) return List.of();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().map(line -> line.split("#", 2)[0].trim()).filter(line -> !line.isEmpty()).toList();
            }
        } catch (Exception error) { OpenComputers.LOGGER.warn("Failed loading robot name list", error); return List.of(); }
    }
    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        name = getDisplayName(tag).filter(value -> !value.isEmpty()).orElseGet(RobotData::randomName);
        totalEnergy = tag.getInt("oc:storedEnergy");
        robotEnergy = tag.getInt("oc:robotEnergy");
        tier = tag.getInt("oc:tier");
        components = readStacks(tag.getList("oc:components", Tag.TAG_COMPOUND), registries);
        containers = readStacks(tag.getList("oc:containers", Tag.TAG_COMPOUND), registries);
        if (tag.contains("oc:lightColor")) lightColor = tag.getInt("oc:lightColor");
    }
    static ItemStack[] readStacks(ListTag list, HolderLookup.Provider registries) {
        ItemStack[] result = new ItemStack[list.size()];
        for (int i = 0; i < list.size(); i++) result[i] = ItemStack.parseOptional(registries, list.getCompound(i));
        return result;
    }
    static ListTag writeStacks(ItemStack[] stacks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) list.add(stack.saveOptional(registries));
        return list;
    }
    @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (name != null && !name.isEmpty()) setDisplayName(tag, name);
        tag.putInt("oc:storedEnergy", totalEnergy);
        tag.putInt("oc:robotEnergy", robotEnergy);
        tag.putInt("oc:tier", tier);
        tag.put("oc:components", writeStacks(components, registries));
        tag.put("oc:containers", writeStacks(containers, registries));
        tag.putInt("oc:lightColor", lightColor);
    }
    public ItemStack copyItemStack(HolderLookup.Provider registries) {
        ItemStack stack = createItemStack(registries);
        RobotData copy = new RobotData(stack, registries);
        for (ItemStack component : copy.components) {
            if (Items.get(component) == Items.get(Constants.BlockName.ScreenTier1) ||
                    Items.get(component) == Items.get(Constants.BlockName.ScreenTier2) ||
                    Items.get(component) == Items.get(Constants.BlockName.ScreenTier3)) {
                var driver = Driver.driverFor(component);
                if (driver != null) driver.saveDataTag(component, new CompoundTag());
            }
        }
        copy.totalEnergy = 0;
        copy.robotEnergy = 50000;
        copy.save(stack, registries);
        return stack;
    }
}
