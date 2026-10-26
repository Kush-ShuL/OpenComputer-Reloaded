package li.cil.oc.server.driver.item;

import java.util.UUID;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.fs.Label;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.item.ComponentItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class FileSystem extends ItemDriver {
    @Override protected boolean supports(ComponentItem item) { return item.storage(); }
    @Override public boolean worksWith(ItemStack stack) {
        return super.worksWith(stack) && !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("oc:lootPath");
    }
    @Override public String slot(ItemStack stack) { return item(stack).kind() == ComponentItem.Kind.HDD ? Slot.HDD : Slot.Floppy; }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
        if (host.world() != null && host.world().isClientSide) return null;
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag state = dataTag(stack);
        String loot = root.getString("oc:lootFactory");
        if (!loot.isEmpty()) {
            var factory = li.cil.oc.common.integration.LootIntegration.fileSystemFactory(loot, host.world());
            if (factory == null) throw new IllegalStateException("Missing loot filesystem factory: " + loot);
            try {
                var fs = factory.call();
                if (fs == null) throw new IllegalStateException("Loot filesystem factory returned null: " + loot);
                return li.cil.oc.api.FileSystem.asManagedEnvironment(fs, state.contains("oc:fs.label") ? state.getString("oc:fs.label") : null,
                        host, "opencomputers:floppy_access");
            } catch (Exception error) { throw new IllegalStateException("Could not mount loot filesystem " + loot, error); }
        }
        CompoundTag node = state.getCompound("node");
        String address = node.getString("address");
        try { UUID.fromString(address); }
        catch (IllegalArgumentException invalid) { address = UUID.randomUUID().toString(); }
        node.putString("address", address); state.put("node", node); saveDataTag(stack, state);
        boolean floppy = item(stack).kind() == ComponentItem.Kind.FLOPPY;
        int tier = tier(stack), speed = floppy ? 1 : tier + 2;
        int capacity = (floppy ? Settings.get().floppySize : Settings.get().hddSizes[tier]) * 1024;
        int platters = floppy ? 1 : Settings.get().hddPlatterCounts[tier];
        Label label = new ItemLabel(stack, state);
        boolean locked = !root.getString("oc:lock").isEmpty();
        String sound = "opencomputers:" + (floppy ? "floppy_access" : "hdd_access");
        ManagedEnvironment environment;
        if (root.getBoolean("oc:unmanaged")) environment = new li.cil.oc.server.component.Drive(capacity, platters, label, host, sound, speed, locked);
        else {
            var fs = li.cil.oc.api.FileSystem.fromSaveDirectory(address, capacity, Settings.get().bufferChanges);
            if (fs == null) throw new IllegalStateException("Filesystem API must be initialized before mounting a disk");
            if (locked) { fs = li.cil.oc.api.FileSystem.asReadOnly(fs); label = new li.cil.oc.server.fs.FileSystem.ReadOnlyLabel(label.getLabel()); }
            environment = li.cil.oc.api.FileSystem.asManagedEnvironment(fs, label, host, sound, speed);
        }
        if (environment == null) throw new IllegalStateException("Filesystem component factory has not been initialized");
        if (environment.node() != null) environment.node().load(node);
        return environment;
    }
    private static final class ItemLabel implements Label {
        private String label;
        ItemLabel(ItemStack stack, CompoundTag data) { load(data); }
        @Override public String getLabel() { return label; }
        @Override public void setLabel(String value) { label = value == null ? null : value.substring(0, Math.min(16, value.length())); }
        @Override public void load(CompoundTag tag) { label = tag.contains("oc:fs.label") ? tag.getString("oc:fs.label") : null; }
        @Override public void save(CompoundTag tag) { if (label == null) tag.remove("oc:fs.label"); else tag.putString("oc:fs.label", label); }
    }
}
