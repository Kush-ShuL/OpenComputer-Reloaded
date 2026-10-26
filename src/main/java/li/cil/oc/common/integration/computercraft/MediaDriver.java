package li.cil.oc.common.integration.computercraft;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.media.IMedia;
import dan200.computercraft.api.media.MediaCapability;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.fs.Label;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Mount ComputerCraft media in the same floppy slots as OpenComputers disks. */
public final class MediaDriver extends DriverItem {
    private static IMedia media(ItemStack stack) {
        if (stack.isEmpty()) return null;
        IMedia capability = stack.getCapability(MediaCapability.get());
        return capability != null ? capability : stack.getItem() instanceof IMedia item ? item : null;
    }

    @Override
    public boolean worksWith(ItemStack stack) {
        return media(stack) != null;
    }

    @Override
    public String slot(ItemStack stack) {
        return Slot.Floppy;
    }

    @Override
    public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
        if (host == null || !(host.world() instanceof ServerLevel level)) return null;
        IMedia media = media(stack);
        if (media == null) return null;
        Mount mount = media.createDataMount(stack, level);
        if (mount == null) return null;

        var environment = li.cil.oc.api.FileSystem.asManagedEnvironment(new MountFileSystem(mount),
                new MediaLabel(stack, media, level.registryAccess(), host), host, "opencomputers:floppy_access");
        if (environment == null) return null;

        // Creating the CC mount can change the item (for example, assigning its disk ID).
        CompoundTag data = dataTag(stack);
        CompoundTag node = data.getCompound("node");
        String address = node.getString("address");
        try {
            UUID.fromString(address);
        } catch (IllegalArgumentException ignored) {
            address = UUID.randomUUID().toString();
        }
        node.putString("address", address);
        data.put("node", node);
        saveDataTag(stack, data);
        if (environment.node() != null) environment.node().load(node);
        host.markChanged();
        return environment;
    }

    private static final class MediaLabel implements Label {
        private final ItemStack stack;
        private final IMedia media;
        private final HolderLookup.Provider registries;
        private final EnvironmentHost host;

        private MediaLabel(ItemStack stack, IMedia media, HolderLookup.Provider registries, EnvironmentHost host) {
            this.stack = stack;
            this.media = media;
            this.registries = registries;
            this.host = host;
        }

        @Override
        public String getLabel() {
            return media.getLabel(registries, stack);
        }

        @Override
        public void setLabel(String value) {
            if (!media.setLabel(stack, value)) throw new IllegalArgumentException("label is read only");
            host.markChanged();
        }

        @Override
        public void load(CompoundTag nbt) {
            // The media owns its label in the item's data components.
        }

        @Override
        public void save(CompoundTag nbt) {
        }
    }
}
