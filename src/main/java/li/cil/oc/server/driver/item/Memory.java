package li.cil.oc.server.driver.item;

import java.util.Map;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.driver.item.CallBudget;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.common.item.ComponentItem;
import net.minecraft.world.item.ItemStack;

public final class Memory extends ItemDriver implements li.cil.oc.api.driver.item.Memory, CallBudget {
    @Override protected boolean supports(ComponentItem item) { return item.kind() == ComponentItem.Kind.MEMORY; }
    @Override public String slot(ItemStack stack) { return Slot.Memory; }
    @Override public double amount(ItemStack stack) { return worksWith(stack) ? Settings.get().ramSizes[item(stack).level()] : 0; }
    @Override public int tier(ItemStack stack) { return worksWith(stack) ? item(stack).tier() : 0; }
    @Override public double getCallBudget(ItemStack stack) { return Settings.get().callBudgets[tier(stack)]; }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) { return new MemoryEnvironment(tier(stack)); }
    private static final class MemoryEnvironment extends AbstractManagedEnvironment implements DeviceInfo {
        private final int tier;
        MemoryEnvironment(int tier) { this.tier=tier; setNode(li.cil.oc.api.Network.newNode(this, Visibility.Neighbors).create()); }
        @Override public Map<String,String> getDeviceInfo() { return Map.of("class","memory","description","Memory bank","vendor","MightyPirates GmbH & Co. KG","product","Multipurpose RAM Type","clock",Integer.toString((int)(Settings.get().callBudgets[tier]*1000))); }
    }
}
