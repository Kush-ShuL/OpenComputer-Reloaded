package li.cil.oc.server.agent;

import li.cil.oc.util.InventoryUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Original Robot.updateInventorySize overflow recovery, using the agent's real inventory. */
public final class InventoryUpgradeCapacity {
    private InventoryUpgradeCapacity() {}

    /** Reinsert into the retained inventory before dropping leftovers in front of the robot. */
    public static void reconcileRobot(RobotController robot) {
        if (!(robot.world() instanceof ServerLevel level)) return;
        if (!level.getServer().isSameThread()) {
            // Equipment callbacks may originate from a computer worker. Never join a world task while holding its locks.
            level.getServer().execute(() -> reconcileRobot(robot));
            return;
        }
        int capacity = robot.inventory.getContainerSize();
        int selected = robot.selectedSlot();
        boolean changed = false;
        for (int slot = capacity; slot < robot.inventory.items.size(); slot++) {
            ItemStack stack = robot.inventory.items.get(slot);
            if (stack.isEmpty()) continue;
            robot.inventory.items.set(slot, ItemStack.EMPTY);
            // Vanilla Inventory.addItemStackToInventory merges matching stacks before filling empty slots.
            // Doing a single insertion pass could occupy a free slot while a matching partial stack remains,
            // unnecessarily forcing another overflow item onto the ground.
            var retained = InventoryUtils.asItemHandler(robot.mainInventory());
            for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
                for (int offset = 0; offset < capacity && !stack.isEmpty(); offset++) {
                    int target = (Math.clamp(selected, 0, capacity) + offset) % capacity;
                    ItemStack existing = retained.getStackInSlot(target);
                    if (pass == 0 ? existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack) : !existing.isEmpty()) continue;
                    InventoryUtils.insertIntoInventorySlot(stack, retained, target, stack.getCount(), false);
                }
            }
            if (!stack.isEmpty() && InventoryUtils.spawnStackInWorld(level, robot.blockPosition(), stack, robot.facing()) == null) {
                // A cancelled spawn must not destroy the remaining items. Hidden backing slots are saved and retried.
                robot.inventory.items.set(slot, stack);
            }
            changed = true;
        }
        int clamped = Math.min(Math.max(0, selected), capacity - 1);
        if (selected != clamped) robot.setSelectedSlot(clamped);
        if (changed) robot.markChanged();
    }
}
