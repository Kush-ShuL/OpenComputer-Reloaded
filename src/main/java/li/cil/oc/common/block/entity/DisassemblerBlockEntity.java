package li.cil.oc.common.block.entity;

import java.util.ArrayDeque;
import java.util.Optional;
import li.cil.oc.Settings;
import li.cil.oc.common.template.DisassemblerTemplates;
import li.cil.oc.util.InventoryUtils;
import li.cil.oc.util.ItemUtils;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Real recipe recovery queue, guaranteed template drops and configured component break chance. */
public final class DisassemblerBlockEntity extends WorkstationBlockEntity {
    private final ArrayDeque<ItemStack> queue = new ArrayDeque<>();
    private double total, buffer;
    private boolean creativeInput, creativeJob;
    public DisassemblerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state, 1); }
    @Override protected String deviceName() { return "disassembler"; }
    @Override protected double rate() { return Settings.get().disassemblerRate; }
    @Override public boolean busy() { return !queue.isEmpty(); }
    @Override public java.util.EnumSet<li.cil.oc.api.util.StateAware.State> getCurrentState() {
        return active ? java.util.EnumSet.of(li.cil.oc.api.util.StateAware.State.IsWorking)
                : busy() ? java.util.EnumSet.of(li.cil.oc.api.util.StateAware.State.CanWork)
                : java.util.EnumSet.noneOf(li.cil.oc.api.util.StateAware.State.class);
    }
    @Override public boolean canStart() { return !getItem(0).isEmpty() && canPlaceItem(0, getItem(0)); }
    @Override public double progress() { return queue.isEmpty() || total <= 0 ? 0 : Math.max(0, Math.min(100, (1 - (queue.size() * Settings.get().disassemblerItemCost - buffer) / total) * 100)); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot != 0 || stack.isEmpty() || ItemUtils.customData(stack).getBoolean("oc:undisassemblable") || level == null) return false;
        if (DisassemblerTemplates.select(stack).isPresent()) return true;
        return (Settings.get().config.getBoolean("misc.disassembleAllTheThings") || li.cil.oc.api.Items.get(stack) != null) && ItemUtils.getIngredients(stack, level).length > 0;
    }
    @Override public void setItem(int slot, ItemStack stack) { super.setItem(slot, stack); creativeInput = false; }
    public void insertedBy(Player player) { creativeInput = player.getAbilities().instabuild && !getItem(0).isEmpty(); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }
    @Override protected void work() {
        if (queue.isEmpty()) {
            boolean instant = creativeInput; creativeInput = false;
            ItemStack stack = removeItem(0, 1);
            if (canPlaceItem(0, stack)) {
                ItemStack[] ingredients = ItemUtils.getIngredients(stack, level);
                var template = DisassemblerTemplates.select(stack);
                if (template.isPresent()) {
                    var result = template.get().disassemble(stack, ingredients, level.registryAccess());
                    result.ingredients().ifPresent(this::enqueue);
                    result.guaranteedDrops().ifPresent(stacks -> { for (ItemStack drop : stacks) drop(drop.copy()); });
                } else enqueue(ingredients);
                total = queue.size() * Settings.get().disassemblerItemCost;
                creativeJob = instant; if (instant) buffer = total;
                activity(!queue.isEmpty());
            } else { drop(stack); activity(false); }
            setChanged(); return;
        }
        double cost = Settings.get().disassemblerItemCost;
        if (buffer < cost) {
            double want = Settings.get().disassemblerTickAmount;
            boolean enough = Settings.get().ignorePower || node().tryChangeBuffer(-want);
            activity(enough); if (enough) buffer += want;
        }
        while (!queue.isEmpty() && buffer >= cost) {
            buffer -= cost; ItemStack stack = queue.removeFirst();
            if (creativeJob || level.random.nextDouble() >= Settings.get().disassemblerBreakChance) drop(stack);
        }
        if (queue.isEmpty()) { creativeJob = false; activity(false); }
        setChanged();
    }
    private void enqueue(ItemStack[] stacks) { for (ItemStack stack : stacks) if (stack != null && !stack.isEmpty()) queue.addLast(stack.copy()); }
    private void drop(ItemStack stack) {
        if (stack.isEmpty()) return;
        for (Direction side : Direction.values()) {
            InventoryUtils.insertIntoInventoryAt(stack, level, worldPosition.relative(side), side.getOpposite(), stack.getCount(), false);
            if (stack.isEmpty()) return;
        }
        InventoryUtils.spawnStackInWorld(level, worldPosition, stack, Direction.UP);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level != null && level.isClientSide) return;
        queue.clear(); for (Tag value : tag.getList("oc:queue", Tag.TAG_COMPOUND)) { ItemStack stack = ItemStack.parseOptional(registries, (CompoundTag) value); if (!stack.isEmpty()) queue.addLast(stack); }
        buffer = tag.getDouble("oc:buffer"); total = tag.getDouble("oc:total"); creativeJob = tag.getBoolean("oc:creativeJob"); active = !queue.isEmpty();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); ListTag list = new ListTag(); for (ItemStack stack : queue) list.add(stack.saveOptional(registries)); tag.put("oc:queue", list); tag.putDouble("oc:buffer", buffer); tag.putDouble("oc:total", total); tag.putBoolean("oc:creativeJob", creativeJob);
    }
}
