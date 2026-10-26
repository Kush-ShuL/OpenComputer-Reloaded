package li.cil.oc.server.driver.block;

import li.cil.oc.Settings;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** The original inventory callbacks, extended to modern item-handler capabilities. */
public final class Inventory implements DriverBlock {
    private static IItemHandler inventory(Level world, BlockPos pos, Direction side) {
        if (!world.hasChunkAt(pos)) return null;
        return world.getBlockEntity(pos) instanceof Container container ? new InvWrapper(container) : world.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
    }
    @Override public boolean worksWith(Level world, BlockPos pos, Direction side) { return inventory(world, pos, side) != null; }
    @Override public ManagedEnvironment createEnvironment(Level world, BlockPos pos, Direction side) { return !world.isClientSide && worksWith(world, pos, side) ? new Environment(world, pos, side) : null; }
    public static final class Environment extends BlockEnvironment {
        Environment(Level level, BlockPos pos, Direction side) { super(level, pos, side, "inventory"); }
        private IItemHandler inventory() { IItemHandler handler = Inventory.inventory(level, position, side); if (handler == null) throw new IllegalStateException("inventory is no longer present"); return handler; }
        private static int slot(Arguments args, int argument, IItemHandler inventory) { int slot = args.checkInteger(argument) - 1; if (slot < 0 || slot >= inventory.getSlots()) throw new IllegalArgumentException("slot index out of bounds"); return slot; }
        private static Object[] denied() { return new Object[]{null, "permission denied"}; }
        @Callback(doc = "function():string -- Get the name of this inventory.")
        public Object[] getInventoryName(Context context, Arguments args) { if (!permitted()) return denied(); var target = level.getBlockEntity(position); return new Object[]{target instanceof Nameable named ? named.getName().getString() : BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString()}; }
        @Callback(doc = "function():number -- Get the number of slots in this inventory.")
        public Object[] getInventorySize(Context context, Arguments args) { return !permitted() ? denied() : new Object[]{inventory().getSlots()}; }
        @Callback(doc = "function(slot:number):number -- Get the stack size in the specified slot.")
        public Object[] getSlotStackSize(Context context, Arguments args) { if (!permitted()) return denied(); var inventory = inventory(); return new Object[]{inventory.getStackInSlot(slot(args, 0, inventory)).getCount()}; }
        @Callback(doc = "function(slot:number):number -- Get the maximum stack size in the specified slot.")
        public Object[] getSlotMaxStackSize(Context context, Arguments args) { if (!permitted()) return denied(); var inventory = inventory(); int slot = slot(args, 0, inventory); ItemStack stack = inventory.getStackInSlot(slot); return new Object[]{stack.isEmpty() ? inventory.getSlotLimit(slot) : Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize())}; }
        @Callback(doc = "function(slotA:number,slotB:number):boolean -- Compare the items in two slots.")
        public Object[] compareStacks(Context context, Arguments args) { if (!permitted()) return denied(); var inventory = inventory(); ItemStack a = inventory.getStackInSlot(slot(args, 0, inventory)), b = inventory.getStackInSlot(slot(args, 1, inventory)); return new Object[]{a.isEmpty() && b.isEmpty() || !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItem(a, b)}; }
        @Callback(doc = "function(slotA:number,slotB:number[,count:number=64]):boolean -- Move items between slots, or swap incompatible stacks.")
        public Object[] transferStack(Context context, Arguments args) {
            if (!permitted()) return denied(); var inventory = inventory(); int from = slot(args, 0, inventory), to = slot(args, 1, inventory), count = Math.clamp(args.optInteger(2, 64), 0, 64);
            if (from == to || count == 0) return new Object[]{true};
            ItemStack a = inventory.getStackInSlot(from).copy(), b = inventory.getStackInSlot(to).copy();
            if (a.isEmpty()) return new Object[]{false};
            if (!b.isEmpty() && !ItemStack.isSameItemSameComponents(a, b)) {
                if (count < a.getCount() || !(inventory instanceof IItemHandlerModifiable mutable) || !inventory.isItemValid(from, b) || !inventory.isItemValid(to, a) || a.getCount() > inventory.getSlotLimit(to) || b.getCount() > inventory.getSlotLimit(from)) return new Object[]{false};
                mutable.setStackInSlot(from, b); mutable.setStackInSlot(to, a); return new Object[]{true};
            }
            ItemStack available = inventory.extractItem(from, count, true);
            int moved = available.getCount() - inventory.insertItem(to, available, true).getCount();
            if (moved <= 0) return new Object[]{false};
            ItemStack extracted = inventory.extractItem(from, moved, false), remainder = inventory.insertItem(to, extracted, false);
            if (!remainder.isEmpty()) {
                ItemStack rollback = inventory.insertItem(from, remainder, false);
                if (!rollback.isEmpty() && inventory instanceof IItemHandlerModifiable mutable) { ItemStack restored = inventory.getStackInSlot(from).copy(); if (restored.isEmpty()) restored = rollback; else restored.grow(rollback.getCount()); mutable.setStackInSlot(from, restored); }
                else if (!rollback.isEmpty()) throw new IllegalStateException("Inventory handler could not restore its own simulated extraction");
            }
            return new Object[]{remainder.getCount() < extracted.getCount()};
        }
        @Callback(doc = "function(slot:number):table -- Get a description of the stack in this slot.")
        public Object[] getStackInSlot(Context context, Arguments args) { if (!Settings.get().allowItemStackInspection) return new Object[]{null, "not enabled in config"}; if (!permitted()) return denied(); var inventory = inventory(); return new Object[]{inventory.getStackInSlot(slot(args, 0, inventory)).copy()}; }
        @Callback(doc = "function():table -- Get descriptions of every stack in this inventory.")
        public Object[] getAllStacks(Context context, Arguments args) { if (!Settings.get().allowItemStackInspection) return new Object[]{null, "not enabled in config"}; if (!permitted()) return denied(); var inventory = inventory(); ItemStack[] stacks = new ItemStack[inventory.getSlots()]; for (int i = 0; i < stacks.length; i++) stacks[i] = inventory.getStackInSlot(i).copy(); return new Object[]{stacks}; }
    }
}
