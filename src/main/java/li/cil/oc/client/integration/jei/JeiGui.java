package li.cil.oc.client.integration.jei;

import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.item.Container;
import li.cil.oc.common.menu.*;
import li.cil.oc.server.agent.RobotController;
import li.cil.oc.server.portable.TabletHost;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Hovering JEI hardware highlights only the OC sockets that actually accept it. */
final class JeiGui {
    private static IJeiRuntime runtime;
    private static boolean listening;
    private JeiGui() {}

    static void runtime(IJeiRuntime value) {
        runtime = value;
        if (value != null && !listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(JeiGui::render);
        }
    }

    private static void render(ScreenEvent.Render.Post event) {
        if (runtime == null || !JeiIntegration.enabled() || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        ItemStack hovered = runtime.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (hovered == null || hovered.isEmpty()) return;
        AbstractContainerMenu menu = screen.getMenu();
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.container instanceof Inventory || !slot.isActive() || !hardwareSlot(menu, index) || !slot.mayPlace(hovered)) continue;
            int x = screen.getGuiLeft() + slot.x, y = screen.getGuiTop() + slot.y;
            event.getGuiGraphics().fill(x, y, x + 16, y + 16, 0x80FFFFFF);
        }
    }

    private static boolean specific(String kind) {
        return kind != null && !kind.equals("none") && !kind.equals("any") && !kind.equals("tool");
    }

    private static boolean hardwareSlot(AbstractContainerMenu menu, int index) {
        if (menu instanceof CaseMenu || menu instanceof ServerMenu || menu instanceof RelayMenu || menu instanceof AdapterMenu
                || menu instanceof RaidMenu || menu instanceof RackMenu || menu instanceof DiskDriveMenu
                || menu instanceof RackDiskMenu || menu instanceof MicrocontrollerMenu) return true;
        if (menu instanceof TabletMenu tablet) {
            var driver = Driver.driverFor(tablet.bay, TabletHost.class);
            return index == 0 && driver instanceof Container container && specific(container.providedSlot(tablet.bay));
        }
        if (menu instanceof AgentMenu agent && agent.robot && agent.host instanceof RobotController robot && index > 0 && index < 4
                && index <= robot.info.containers.length) {
            ItemStack bay = robot.info.containers[index - 1];
            var driver = Driver.driverFor(bay, robot.getClass());
            return driver instanceof Container container && specific(container.providedSlot(bay));
        }
        if (menu instanceof WorkstationMenu workstation) return workstation.assembler && index > 0 && index < 22 && specific(workstation.slotInfo(index).kind);
        return false;
    }
}
