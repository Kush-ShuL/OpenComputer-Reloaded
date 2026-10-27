package li.cil.oc.common.block.entity;

import li.cil.oc.Settings;
import li.cil.oc.api.machine.*;
import li.cil.oc.common.template.AssemblerTemplates;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Template-driven assembly consumes its inputs once and persists the real pending device. */
public final class AssemblerBlockEntity extends WorkstationBlockEntity {
    private ItemStack output = ItemStack.EMPTY;
    private double total, remaining;
    private boolean clientBusy;
    public AssemblerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state, 22); }
    @Override protected String deviceName() { return "assembler"; }
    @Override protected double rate() { return Settings.get().assemblerRate; }
    public AssemblerTemplates.Template template() { return AssemblerTemplates.select(getItem(0)).orElse(null); }
    @Override public boolean busy() { return remaining > 0 || clientBusy; }
    /** A snapshot of the actual device being assembled, without exposing mutable inventory state. */
    public ItemStack pendingOutput() { return output.copy(); }
    @Override public boolean canStart() { var template = template(); return !busy() && output.isEmpty() && template != null && template.validate(this).valid(); }
    @Override public double progress() { return !busy() || total <= 0 ? 0 : Math.max(0, Math.min(100, (1 - remaining / total) * 100)); }
    @Override public int timeRemaining() { return (int) Math.min(Integer.MAX_VALUE, Math.max(0, remaining / Settings.get().assemblerTickAmount / 20)); }
    public AssemblerTemplates.Slot slotInfo(int slot) {
        var template = template();
        if (template == null) return AssemblerTemplates.NO_SLOT;
        if (slot >= 1 && slot < 4) return template.containerSlots[slot - 1];
        if (slot >= 4 && slot < 13) return template.upgradeSlots[slot - 4];
        if (slot >= 13 && slot < 22) return template.componentSlots[slot - 13];
        return AssemblerTemplates.NO_SLOT;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return !busy() && output.isEmpty() && slot >= 0 && slot < items.size() && !stack.isEmpty()
                && (slot == 0 ? AssemblerTemplates.select(stack).isPresent() : slotInfo(slot).validate(this, slot, stack));
    }
    public boolean start(boolean immediate) {
        if (level == null || level.isClientSide || !canStart()) return false;
        for (int slot = 0; slot < items.size(); slot++) if (!getItem(slot).isEmpty() && !canPlaceItem(slot, getItem(slot))) return false;
        var assembled = template().assemble(this);
        if (assembled.stack().isEmpty() || !Double.isFinite(assembled.energy())) return false;
        output = assembled.stack().copy(); total = immediate ? 0 : Math.max(1, assembled.energy()); remaining = total;
        items.replaceAll(stack -> ItemStack.EMPTY); activity(true); setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); return true;
    }
    @Callback(doc = "function():string,number or boolean -- Busy and progress, or idle and template validity.")
    public Object[] status(Context context, Arguments args) { return busy() ? new Object[]{"busy", progress()} : new Object[]{"idle", canStart()}; }
    @Callback(doc = "function():boolean -- Start assembling the current template.")
    public Object[] start(Context context, Arguments args) { return new Object[]{start(false)}; }
    @Override protected void work() {
        if (output.isEmpty()) { activity(false); return; }
        double want = Math.max(1, Math.min(remaining, Settings.get().assemblerTickAmount * Settings.get().tickFrequency));
        double have = Settings.get().ignorePower ? want : want + node().changeBuffer(-want);
        remaining -= have;
        if (remaining <= 0) { items.set(0, output); output = ItemStack.EMPTY; remaining = 0; }
        activity(have > .5 && !output.isEmpty()); setChanged();
        if (output.isEmpty()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        total = tag.getDouble("oc:total"); remaining = tag.getDouble("oc:remaining");
        if (level != null && level.isClientSide) { clientBusy = tag.getBoolean("oc:assembling"); return; }
        output = ItemStack.parseOptional(registries, tag.getCompound(tag.contains("oc:output") ? "oc:output" : "oc:robot"));
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.put("oc:output", output.saveOptional(registries)); tag.putDouble("oc:total", total); tag.putDouble("oc:remaining", remaining);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = super.getUpdateTag(registries); tag.putBoolean("oc:assembling", busy()); tag.putDouble("oc:total", total); tag.putDouble("oc:remaining", remaining); return tag; }
}
