package li.cil.oc.common.block.entity;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.common.redstone.RedstoneHost;
import li.cil.oc.common.redstone.RedstoneState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.concurrent.atomic.AtomicBoolean;

/** Always-enabled, network-visible vanilla redstone I/O with independent wake threshold. */
public final class RedstoneBlockEntity extends BlockEntity implements Environment, RedstoneHost {
    private final RedstoneState redstone = new RedstoneState(this, change -> { if (this.component != null) this.component.redstoneChanged(change); });
    private li.cil.oc.server.component.Redstone component;
    private CompoundTag pendingComponent;
    private final AtomicBoolean pendingSave = new AtomicBoolean();
    private boolean detached;
    public RedstoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public RedstoneState redstoneState() { return redstone; }
    @Override public Level world() { return level; }
    @Override public double xPosition() { return worldPosition.getX() + .5; }
    @Override public double yPosition() { return worldPosition.getY() + .5; }
    @Override public double zPosition() { return worldPosition.getZ() + .5; }
    @Override public void markChanged() { pendingSave.set(true); }
    private li.cil.oc.server.component.Redstone component() {
        if (component == null && level != null && !level.isClientSide && !detached && !isRemoved()) {
            component = li.cil.oc.common.redstone.BundledRedstone.isAvailable()
                    ? new li.cil.oc.server.component.RedstoneBundled(this, true)
                    : new li.cil.oc.server.component.Redstone(this, true);
            if (pendingComponent != null) { component.load(pendingComponent, level.registryAccess()); pendingComponent = null; }
        }
        return component;
    }
    @Override public Node node() { var value = component(); return value == null ? null : value.node(); }
    @Override public void onConnect(Node node) {}
    @Override public void onDisconnect(Node node) {}
    @Override public void onMessage(Message message) { if (component != null) component.onMessage(message); }
    public void tick() {
        if (level == null || level.isClientSide || detached || isRemoved()) return;
        if (pendingSave.getAndSet(false)) super.setChanged();
        if (node().network() == null) Network.joinOrCreateNetwork(this);
        redstone.setEnabled(true); redstone.tick();
    }
    public void neighborChanged() {
        redstone.rescan();
        if (level != null && !level.isClientSide && !detached && !isRemoved()) Network.joinOrCreateNetwork(this);
    }
    @Override public void onLoad() { super.onLoad(); detached = false; if (level != null && !level.isClientSide) neighborChanged(); }
    private void detach() { if (detached) return; detached = true; if (component != null) component.node().remove(); }
    @Override public void setRemoved() { detach(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { detach(); super.onChunkUnloaded(); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level != null && level.isClientSide) { redstone.loadClient(tag); return; }
        redstone.load(tag);
        pendingComponent = tag.getCompound("oc:redstone").copy();
        if (component != null) { component.load(pendingComponent, registries); pendingComponent = null; }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); redstone.save(tag);
        CompoundTag data = pendingComponent == null ? new CompoundTag() : pendingComponent.copy();
        if (component != null) component.save(data, registries);
        tag.put("oc:redstone", data);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); redstone.saveClient(tag); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void setChanged() {
        if (level instanceof ServerLevel world && !world.getServer().isSameThread()) { pendingSave.set(true); return; }
        super.setChanged();
    }
}
