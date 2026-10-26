package li.cil.oc.common.menu;

import java.util.function.Supplier;
import li.cil.oc.common.block.entity.ChargerBlockEntity;
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

public final class ChargerMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(Registries.MENU, "opencomputers");
    public static final Supplier<MenuType<ChargerMenu>> TYPE = TYPES.register("charger", () -> IMenuTypeExtension.create((id, player, data) -> new ChargerMenu(id, player, new SimpleContainer(1), data.readBlockPos())));
    private final Container charger;
    public final BlockPos position;
    public static void register(IEventBus bus) { TYPES.register(bus); }
    public ChargerMenu(int id, Inventory player, ChargerBlockEntity charger) { this(id, player, charger, charger.getBlockPos()); }
    private ChargerMenu(int id, Inventory player, Container charger, BlockPos pos) {
        super(TYPE.get(), id); this.charger = charger; position = pos;
        addSlot(new Slot(charger, 0, 80, 35) { @Override public boolean mayPlace(ItemStack stack) { return ChargerBlockEntity.canCharge(stack); } @Override public int getMaxStackSize() { return 1; } });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(player, 9 + row * 9 + column, 8 + column * 18, 84 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 8 + column * 18, 142));
    }
    @Override public boolean stillValid(Player player) { return charger.stillValid(player) && player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) <= 64; }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        if (charger instanceof ChargerBlockEntity block) block.flushItem();
        Slot source = slots.get(index); if (!source.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = source.getItem(), original = stack.copy();
        boolean moved = index == 0 ? moveItemStackTo(stack, 1, slots.size(), true) : slots.getFirst().mayPlace(stack) && moveItemStackTo(stack, 0, 1, false);
        if (!moved) return ItemStack.EMPTY; if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY); else source.setChanged(); source.onTake(player, stack); return original;
    }
}
