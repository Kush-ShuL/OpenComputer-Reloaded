package li.cil.oc.common.menu;

import java.util.function.Supplier;
import li.cil.oc.common.block.entity.PrinterBlockEntity;
import li.cil.oc.common.item.data.PrintData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PrinterMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(Registries.MENU, "opencomputers");
    public static final Supplier<MenuType<PrinterMenu>> TYPE = TYPES.register("printer", () -> IMenuTypeExtension.create((id, player, data) -> new PrinterMenu(id, player, new SimpleContainer(3), data.readBlockPos(), new SimpleContainerData(5))));
    private final Container inventory;
    private final ContainerData data;
    public final BlockPos position;
    public static void register(IEventBus bus) { TYPES.register(bus); }
    public PrinterMenu(int id, Inventory player, PrinterBlockEntity printer) { this(id, player, printer, printer.getBlockPos(), new ContainerData() {
        @Override public int get(int index) { var model = printer.printer(); return switch (index) { case 0 -> model.material & 65535; case 1 -> model.material >>> 16; case 2 -> model.ink & 65535; case 3 -> model.ink >>> 16; case 4 -> model.isPrinting() ? (int) (model.progress() * 100) : 0; default -> 0; }; }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 5; }
    }); }
    private PrinterMenu(int id, Inventory player, Container inventory, BlockPos pos, ContainerData data) {
        super(TYPE.get(), id); this.inventory = inventory; this.data = data; position = pos;
        addSlot(new Slot(inventory, 0, 18, 19) { @Override public boolean mayPlace(ItemStack stack) { return PrintData.materialValue(stack) > 0; } });
        addSlot(new Slot(inventory, 1, 18, 51) { @Override public boolean mayPlace(ItemStack stack) { return PrintData.inkValue(stack) > 0; } });
        addSlot(new Slot(inventory, 2, 152, 35) { @Override public boolean mayPlace(ItemStack stack) { return false; } });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(player, 9 + row * 9 + column, 8 + column * 18, 84 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 8 + column * 18, 142));
        addDataSlots(data);
    }
    public int material() { return (data.get(0) & 65535) | (data.get(1) & 65535) << 16; }
    public int ink() { return (data.get(2) & 65535) | (data.get(3) & 65535) << 16; }
    public double progress() { return (data.get(4) & 65535) / 10000d; }
    @Override public boolean stillValid(Player player) { return inventory.stillValid(player) && player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) <= 64; }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index); if (!source.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = source.getItem(), original = stack.copy();
        boolean moved = index < 3 ? moveItemStackTo(stack, 3, slots.size(), true)
                : slots.get(0).mayPlace(stack) ? moveItemStackTo(stack, 0, 1, false) : slots.get(1).mayPlace(stack) && moveItemStackTo(stack, 1, 2, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY); else source.setChanged(); source.onTake(player, stack); return original;
    }
}
