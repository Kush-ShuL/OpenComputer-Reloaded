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

public class KeyboardBlockEntity extends BlockEntity implements Environment, EnvironmentHost, SidedEnvironment,
        li.cil.oc.api.internal.Rotatable, li.cil.oc.api.network.Analyzable {
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
    @Override public Direction facing() { return ScreenBlock.facing(getBlockState()); }
    @Override public Direction toGlobal(Direction side) { return ScreenBlock.toGlobal(getBlockState(), side); }
    @Override public Direction toLocal(Direction side) { return ScreenBlock.toLocal(getBlockState(), side); }
    @Override public Node[] onAnalyze(net.minecraft.world.entity.player.Player player, Direction side, float x, float y, float z) { return new Node[]{node()}; }
    @Override public Node sidedNode(Direction side) { return hasNodeOnSide(side) ? node() : null; }
    @OnlyIn(Dist.CLIENT) @Override public boolean canConnect(Direction side) { return hasNodeOnSide(side); }
    @Override public void setBlockState(BlockState state) {
        BlockState previous=getBlockState();
        boolean rotated=previous.hasProperty(ScreenBlock.PITCH)&&state.hasProperty(ScreenBlock.PITCH)
                &&(previous.getValue(ScreenBlock.PITCH)!=state.getValue(ScreenBlock.PITCH)||previous.getValue(ScreenBlock.YAW)!=state.getValue(ScreenBlock.YAW));
        super.setBlockState(state);
        if(rotated&&level!=null&&!level.isClientSide&&keyboard!=null){
            for(Direction side:Direction.values())if(!hasNodeOnSide(side)){
                BlockPos neighbor=worldPosition.relative(side);
                if(level.hasChunkAt(neighbor)){Node remote=li.cil.oc.server.network.Network.getNetworkNode(level.getBlockEntity(neighbor),side.getOpposite());if(remote!=null)node().disconnect(remote);}
            }
            level.invalidateCapabilities(worldPosition);Network.joinOrCreateNetwork(this);
        }
    }
    public ScreenBlockEntity connectedScreen() {
        if (level == null) return null;
        boolean wall = facing().getAxis().isHorizontal();
        Direction support = facing().getOpposite(), forward = wall ? Direction.UP : getBlockState().getValue(ScreenBlock.YAW);
        Direction[] sides = wall ? new Direction[]{support, forward, forward.getOpposite()} : new Direction[]{support, forward};
        for (Direction side : sides) {
            BlockPos pos = worldPosition.relative(side);
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ScreenBlockEntity screen) return screen;
        }
        return null;
    }
    @Override public void onLoad() { super.onLoad(); if (level != null && !level.isClientSide) Network.joinOrCreateNetwork(this); }
    @Override public void onChunkUnloaded() { if (node() != null) node().remove(); super.onChunkUnloaded(); }
    @Override public void setRemoved() { if (node() != null) node().remove(); super.setRemoved(); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); keyboard().load(tag.getCompound("oc:keyboard"), registries); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); CompoundTag data = new CompoundTag(); keyboard().save(data, registries); tag.put("oc:keyboard", data); }
}
