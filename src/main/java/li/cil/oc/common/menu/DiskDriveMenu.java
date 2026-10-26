package li.cil.oc.common.menu;

import java.util.function.Supplier;
import li.cil.oc.api.Driver;
import li.cil.oc.common.block.entity.DiskDriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Original single slot at (80,35), followed by the player's inventory. */
public final class DiskDriveMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(Registries.MENU, "opencomputers");
    public static final Supplier<MenuType<DiskDriveMenu>> TYPE = TYPES.register("diskdrive", () -> IMenuTypeExtension.create((id, player, data) -> new DiskDriveMenu(id, player, new SimpleContainer(1), data.readBlockPos())));
    public final BlockPos position;
    private final Container drive;
    public static void register(IEventBus bus) { TYPES.register(bus); }
    public DiskDriveMenu(int id, Inventory player, DiskDriveBlockEntity drive) { this(id, player, drive, drive.getBlockPos()); }
    private DiskDriveMenu(int id, Inventory player, Container drive, BlockPos pos) {
        super(TYPE.get(), id); this.drive = drive; this.position = pos;
        drive.startOpen(player.player);
        addSlot(new Slot(drive, 0, 80, 35) {
            @Override public boolean mayPlace(ItemStack stack) { var driver = Driver.driverFor(stack, DiskDriveBlockEntity.class); return driver != null && li.cil.oc.api.driver.item.Slot.Floppy.equals(driver.slot(stack)); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(player, 9 + row * 9 + column, 8 + column * 18, 84 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 8 + column * 18, 142));
    }
    @Override public boolean stillValid(Player player) { return drive.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); drive.stopOpen(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem()) return ItemStack.EMPTY;
        if (drive instanceof DiskDriveBlockEntity block) block.flushMedium();
        ItemStack stack = source.getItem(), original = stack.copy();
        boolean moved = index == 0 ? moveItemStackTo(stack, 1, slots.size(), true) : slots.getFirst().mayPlace(stack) && moveItemStackTo(stack, 0, 1, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY); else source.setChanged();
        source.onTake(player, stack);
        return original;
    }
}
