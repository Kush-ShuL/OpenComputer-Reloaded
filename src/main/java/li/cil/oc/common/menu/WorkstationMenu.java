package li.cil.oc.common.menu;

import java.util.function.Supplier;
import li.cil.oc.common.block.entity.*;
import li.cil.oc.common.template.AssemblerTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WorkstationMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(Registries.MENU, "opencomputers");
    public static final Supplier<MenuType<WorkstationMenu>> TYPE = TYPES.register("workstation", () -> IMenuTypeExtension.create((id, inventory, data) -> {
        BlockPos pos = data.readBlockPos(); boolean assembler = data.readBoolean();
        return new WorkstationMenu(id, inventory, new SimpleContainer(assembler ? 22 : 1), pos, assembler, new SimpleContainerData(6));
    }));
    public final Container inventory;
    public final BlockPos position;
    public final boolean assembler;
    private final ContainerData data;
    private final int machineSlots;
    public static void register(IEventBus bus) { TYPES.register(bus); }
    public WorkstationMenu(int id, Inventory player, WorkstationBlockEntity machine) {
        this(id, player, machine, machine.getBlockPos(), machine instanceof AssemblerBlockEntity, new ContainerData() {
            @Override public int get(int index) { return switch (index) { case 0 -> machine.busy() ? 1 : 0; case 1 -> (int) Math.round(machine.progress() * 10); case 2 -> machine.timeRemaining() & 0xffff; case 3 -> machine.timeRemaining() >>> 16; case 4 -> machine.canStart() ? 1 : 0; case 5 -> machine.active() ? 1 : 0; default -> 0; }; }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 6; }
        });
    }
    private WorkstationMenu(int id, Inventory player, Container inventory, BlockPos pos, boolean assembler, ContainerData data) {
        super(TYPE.get(), id); this.inventory = inventory; this.position = pos; this.assembler = assembler; this.data = data;
        machineSlots = assembler ? 22 : 1;
        inventory.startOpen(player.player);
        if (assembler) {
            machineSlot(player.player, 0, 12, 12);
            for (int i = 0; i < 3; i++) machineSlot(player.player, 1 + i, 34 + i * 18, 70);
            for (int i = 0; i < 9; i++) machineSlot(player.player, 4 + i, 34 + i % 3 * 18, 12 + i / 3 * 18);
            for (int i = 0; i < 3; i++) machineSlot(player.player, 13 + i, 104, 12 + i * 18);
            machineSlot(player.player, 16, 126, 12);
            for (int i = 0; i < 2; i++) machineSlot(player.player, 17 + i, 126, 30 + i * 18);
            for (int i = 0; i < 3; i++) machineSlot(player.player, 19 + i, 148, 12 + i * 18);
        } else machineSlot(player.player, 0, 80, 35);
        int start = assembler ? 110 : 84;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(player, 9 + row * 9 + column, 8 + column * 18, start + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 8 + column * 18, start + 58));
        addDataSlots(data);
    }
    private void machineSlot(Player player, int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean isActive() { return !assembler || index == 0 || hasItem() || !li.cil.oc.api.driver.item.Slot.None.equals(slotInfo(index).kind); }
            @Override public boolean mayPlace(ItemStack stack) {
                if (inventory instanceof WorkstationBlockEntity machine) return machine.canPlaceItem(index, stack);
                if (!assembler) return true;
                return !busy() && (index == 0 ? AssemblerTemplates.select(stack).isPresent() : slotInfo(index).validate(inventory, index, stack));
            }
            @Override public boolean mayPickup(Player player) { return !assembler || !busy(); }
            @Override public void setByPlayer(ItemStack stack, ItemStack previous) { super.setByPlayer(stack, previous); if (inventory instanceof DisassemblerBlockEntity disassembler) disassembler.insertedBy(player); }
        });
    }
    public AssemblerTemplates.Slot slotInfo(int index) {
        var template = AssemblerTemplates.select(inventory.getItem(0)).orElse(null);
        if (template == null) return AssemblerTemplates.NO_SLOT;
        if (index >= 1 && index < 4) return template.containerSlots[index - 1];
        if (index >= 4 && index < 13) return template.upgradeSlots[index - 4];
        if (index >= 13 && index < 22) return template.componentSlots[index - 13];
        return AssemblerTemplates.NO_SLOT;
    }
    public AssemblerTemplates.Validation validation() { return AssemblerTemplates.select(inventory.getItem(0)).map(template -> template.validate(inventory)).orElse(null); }
    public boolean busy() { return data.get(0) != 0; }
    public double progress() { return data.get(1) / 10d; }
    public int timeRemaining() { return (data.get(2) & 0xffff) | (data.get(3) & 0xffff) << 16; }
    public boolean canBuild() { return assembler && !busy() && data.get(4) != 0; }
    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }
    @Override public boolean clickMenuButton(Player player, int id) { return id == 0 && inventory instanceof AssemblerBlockEntity machine && machine.stillValid(player) && machine.start(player.getAbilities().instabuild); }
    @Override public void removed(Player player) { super.removed(player); inventory.stopOpen(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), old = stack.copy();
        if (index < machineSlots ? !moveItemStackTo(stack, machineSlots, slots.size(), true) : !moveItemStackTo(stack, 0, machineSlots, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (index >= machineSlots && inventory instanceof DisassemblerBlockEntity disassembler) disassembler.insertedBy(player);
        slot.onTake(player, stack); return old;
    }
}
