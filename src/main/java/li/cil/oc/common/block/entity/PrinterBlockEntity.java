package li.cil.oc.common.block.entity;

import java.util.EnumSet;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.util.StateAware;
import li.cil.oc.common.item.data.PrintData;
import li.cil.oc.common.menu.PrinterMenu;
import li.cil.oc.server.component.Printer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Three-slot printer host; GUI synchronization uses normal block/menu channels. */
public final class PrinterBlockEntity extends AutomationBlockEntity implements WorldlyContainer, MenuProvider, SidedEnvironment, StateAware {
    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private final PrintData preview = new PrintData();
    public boolean clientWorking;
    public double clientRemaining;
    public PrinterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override protected li.cil.oc.api.network.ManagedEnvironment createEnvironment() { return new Printer(this); }
    public Printer printer() { return (Printer) environment(); }
    public PrintData preview() { return server() ? printer().data : preview; }
    @Override public Node sidedNode(Direction side) { return side == Direction.UP ? null : node(); }
    @Override public boolean canConnect(Direction side) { return side != Direction.UP; }
    @Override public EnumSet<State> getCurrentState() { Printer printer = server() ? printer() : null; return printer != null && printer.isPrinting() ? EnumSet.of(State.IsWorking) : printer != null && printer.canPrint() ? EnumSet.of(State.CanWork) : EnumSet.noneOf(State.class); }
    public void syncPreview() { if (server()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level != null && level.isClientSide) { preview.load(tag.getCompound("oc:data")); clientRemaining = tag.getDouble("oc:remaining"); clientWorking = tag.getBoolean("oc:printing"); }
        else ContainerHelper.loadAllItems(tag, items, registries);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); ContainerHelper.saveAllItems(tag, items, registries); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(), model = new CompoundTag();
        if (server()) { printer().data.save(model); tag.putDouble("oc:remaining", printer().remainingEnergy); tag.putBoolean("oc:printing", printer().working); }
        else preview.save(model);
        tag.put("oc:data", model); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public int getContainerSize() { return 3; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack result = ContainerHelper.removeItem(items, slot, amount); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 ? PrintData.materialValue(stack) > 0 : slot == 1 && PrintData.inkValue(stack) > 0; }
    @Override public int[] getSlotsForFace(Direction side) { return new int[]{0, 1, 2}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return slot != 2 && canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return !canPlaceItem(slot, stack); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(xPosition(), yPosition(), zPosition()) <= 64; }
    @Override public Component getDisplayName() { return Component.translatable("block.opencomputers.printer"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory player, Player user) { return stillValid(user) ? new PrinterMenu(id, player, this) : null; }
}
