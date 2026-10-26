package li.cil.oc.server.driver.item;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.driver.item.CallBudget;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.common.item.ComponentItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class Processor extends ItemDriver implements MutableProcessor, CallBudget {
    @Override protected boolean supports(ComponentItem item) { return item.kind() == ComponentItem.Kind.PROCESSOR; }
    @Override public String slot(ItemStack stack) { return Slot.CPU; }
    @Override public int supportedComponents(ItemStack stack) { return Settings.get().cpuComponentSupport[tier(stack)]; }
    @Override public double getCallBudget(ItemStack stack) { return Settings.get().callBudgets[tier(stack)]; }
    @Override public Collection<Class<? extends Architecture>> allArchitectures() { return List.copyOf(li.cil.oc.api.Machine.architectures()); }
    @Override public Class<? extends Architecture> architecture(ItemStack stack) {
        String stored = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("oc:archClass");
        if (stored.equals("li.cil.oc.server.machine.luac.NativeLuaArchitecture")) return li.cil.oc.api.Machine.LuaArchitecture;
        for (Class<? extends Architecture> architecture : allArchitectures()) if (architecture.getName().equals(stored)) return architecture;
        if (!stored.isEmpty()) CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> { root.remove("oc:archClass"); root.remove("oc:archName"); });
        return allArchitectures().stream().findFirst().orElse(li.cil.oc.api.Machine.LuaArchitecture);
    }
    @Override public void setArchitecture(ItemStack stack, Class<? extends Architecture> architecture) {
        item(stack);
        if (architecture == null || !allArchitectures().contains(architecture)) throw new IllegalArgumentException("Unsupported processor architecture");
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            root.putString("oc:archClass", architecture.getName());
            root.putString("oc:archName", java.util.Objects.requireNonNullElse(li.cil.oc.api.Machine.getArchitectureName(architecture), architecture.getSimpleName()));
        });
    }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) { return new ProcessorEnvironment(tier(stack)); }
    private static final class ProcessorEnvironment extends AbstractManagedEnvironment implements DeviceInfo {
        private final int tier;
        ProcessorEnvironment(int tier) { this.tier=tier; setNode(li.cil.oc.api.Network.newNode(this, Visibility.Neighbors).create()); }
        @Override public Map<String,String> getDeviceInfo() { return Map.of("class","processor","description","CPU","vendor","MightyPirates GmbH & Co. KG","product","FlexiArch "+(tier+1)+" Processor","clock",Integer.toString((int)(Settings.get().callBudgets[tier]*1000))); }
    }
}
