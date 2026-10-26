package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.item.UpgradeMFItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class UpgradeMF implements DriverItem, HostAware, EnvironmentProvider {
    @Override public boolean worksWith(ItemStack stack) { return stack != null && !stack.isEmpty() && stack.getItem() instanceof UpgradeMFItem; }
    @Override public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) { return worksWith(stack) && li.cil.oc.api.internal.Adapter.class.isAssignableFrom(host) && !li.cil.oc.server.driver.Registry.INSTANCE.isBlacklistedHost(stack, host); }
    @Override public String slot(ItemStack stack) { return Slot.Upgrade; }
    @Override public int tier(ItemStack stack) { return 2; }
    @Override public CompoundTag dataTag(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("oc:data"); }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
        if (host.world() == null || host.world().isClientSide || host.world().getServer() == null) return null;
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); int[] coord = root.getIntArray("oc:coord"); var dimension = UpgradeMFItem.dimension(root);
        if (coord.length != 5 || coord[4] < 0 || coord[4] > 5 || dimension == null || host.world().getServer().getLevel(dimension) == null) return null;
        return new li.cil.oc.server.component.UpgradeMF(host, dimension, new BlockPos(coord[0], coord[1], coord[2]), Direction.from3DDataValue(coord[4]));
    }
    @Override public Class<?> getEnvironment(ItemStack stack) { return worksWith(stack) ? li.cil.oc.server.component.UpgradeMF.class : null; }
}
