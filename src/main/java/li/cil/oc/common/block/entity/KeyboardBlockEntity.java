package li.cil.oc.common.block.entity;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.common.block.ScreenBlock;
import li.cil.oc.server.component.Keyboard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.concurrent.atomic.AtomicBoolean;

public class KeyboardBlockEntity extends BlockEntity implements Environment, EnvironmentHost, SidedEnvironment {
    private Keyboard keyboard;
    private final AtomicBoolean pendingSave = new AtomicBoolean();
    public KeyboardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public synchronized Keyboard keyboard() { if (keyboard == null) keyboard = new Keyboard(this); return keyboard; }
    @Override public Node node() { return keyboard().node(); }
    @Override public void onConnect(Node node) { keyboard().onConnect(node); }
    @Override public void onDisconnect(Node node) { keyboard().onDisconnect(node); }
    @Override public void onMessage(Message message) { keyboard().onMessage(message); }
    @Override public Level world() { return level; }
    @Override public double xPosition() { return worldPosition.getX() + 0.5; }
    @Override public double yPosition() { return worldPosition.getY() + 0.5; }
    @Override public double zPosition() { return worldPosition.getZ() + 0.5; }
    @Override public void markChanged() { setChanged(); }
    @Override public void setChanged() {
        if (level instanceof ServerLevel server && !server.getServer().isSameThread()) { pendingSave.set(true); return; }
        pendingSave.getAndSet(false);
        super.setChanged();
    }
    public void tick() {
        if (level == null || level.isClientSide) return;
        if (pendingSave.getAndSet(false)) super.setChanged();
        keyboard().update();
    }
    public boolean hasNodeOnSide(Direction side) {
        Direction facing = ScreenBlock.facing(getBlockState());
        return side != facing && (!facing.getAxis().isVertical() || side.getOpposite() != getBlockState().getValue(ScreenBlock.YAW));
    }
    @Override public Node sidedNode(Direction side) { return hasNodeOnSide(side) ? node() : null; }
    @OnlyIn(Dist.CLIENT) @Override public boolean canConnect(Direction side) { return hasNodeOnSide(side); }
    public ScreenBlockEntity connectedScreen() {
        if (level == null) return null;
        Direction support = ScreenBlock.facing(getBlockState()).getOpposite();
        for (Direction side : new Direction[]{support, getBlockState().getValue(ScreenBlock.YAW), Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos pos = worldPosition.relative(side);
            if (hasNodeOnSide(side) && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ScreenBlockEntity screen) return screen;
        }
        return null;
    }
    @Override public void onLoad() { super.onLoad(); if (level != null && !level.isClientSide) Network.joinOrCreateNetwork(this); }
    @Override public void onChunkUnloaded() { if (node() != null) node().remove(); super.onChunkUnloaded(); }
    @Override public void setRemoved() { if (node() != null) node().remove(); super.setRemoved(); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); keyboard().load(tag.getCompound("oc:keyboard"), registries); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); CompoundTag data = new CompoundTag(); keyboard().save(data, registries); tag.put("oc:keyboard", data); }
}
