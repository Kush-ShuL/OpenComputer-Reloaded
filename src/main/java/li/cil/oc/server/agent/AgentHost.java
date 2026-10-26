package li.cil.oc.server.agent;

import li.cil.oc.api.internal.Agent;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** The shared implementation boundary of the original robot and drone agents. */
public interface AgentHost extends Agent, Environment {
    record ComponentSlot(int index, ItemStack stack) {}
    AgentRuntime runtime();
    List<ComponentSlot> componentStacks();
    boolean robot();
    BlockPos blockPosition();
    void inventoryChanged();
    void synchronize();
    void animate(double seconds, int kind);
    default ManagedEnvironment environment(int slot) { return runtime().environment(slot); }
    default double delay(String action) { return li.cil.oc.Settings.get().config.getDouble("robot.delays." + action); }
}
