package li.cil.oc.common.block.entity;

import java.util.Arrays;
import li.cil.oc.Settings;
import li.cil.oc.common.block.PrintBlock;
import li.cil.oc.common.item.data.PrintData;
import li.cil.oc.common.printing.PrintGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Original two-state printed block, with its own geometry, redstone and light data. */
public final class PrintBlockEntity extends BlockEntity implements RotatableBlockEntity {
    public static final ModelProperty<PrintGeometry.View> MODEL = new ModelProperty<>();
    public final PrintData data = new PrintData();
    private final int[] input = {-1, -1, -1, -1, -1, -1};
    public boolean active;
    private VoxelShape off = Shapes.block(), on = Shapes.block();
    private ModelData modelData = ModelData.EMPTY;
    public PrintBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public Direction facing() { return getBlockState().getValue(PrintBlock.FACING); }
    @Override public boolean rotate(Direction axis) {
        Direction next = li.cil.oc.util.RotationHelper.rotate(facing(), axis);
        if (next == facing() || !PrintBlock.FACING.getPossibleValues().contains(next) || level == null || level.isClientSide) return false;
        if (!level.setBlock(worldPosition, getBlockState().setValue(PrintBlock.FACING, next), 3)) return false;
        updateGeometry(); changed(); return true;
    }
    public void setData(ItemStack stack) { data.load(PrintData.customData(stack)); active = false; Arrays.fill(input, -1); updateGeometry(); changed(); }
    public ItemStack stack(HolderLookup.Provider registries) { return data.createItemStack(registries); }
    public VoxelShape shape() { return active ? on : off; }
    public VoxelShape collision() { return (active ? data.noclipOn : data.noclipOff) ? Shapes.empty() : shape(); }
    public int output() { return data.emitRedstone(active) ? data.redstoneLevel : 0; }
    public boolean activate() {
        if (!data.hasActiveState() || active && data.isButtonMode) return false;
        if (level != null && !level.isClientSide) toggle();
        return true;
    }
    public void toggle() {
        if (level == null || level.isClientSide || !data.hasActiveState()) return;
        active = !active;
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, .3f, active ? .6f : .5f);
        updateGeometry(); changed();
        if (active && data.isButtonMode) level.scheduleTick(worldPosition, getBlockState().getBlock(), 20);
    }
    public void neighborChanged() {
        if (level == null || level.isClientSide || isRemoved()) return;
        for (Direction side : Direction.values()) {
            BlockPos neighbor = worldPosition.relative(side);
            int next = li.cil.oc.common.redstone.BundledRedstone.computeInput(level, worldPosition, side);
            int index = side.get3DDataValue(), old = input[index]; input[index] = next;
            if (old >= 0 && old != next && !data.emitRedstone() && data.hasActiveState() && active != (next > 0)) toggle();
        }
    }
    public boolean solid(Direction side) {
        for (PrintData.Shape shape : active ? data.stateOn : data.stateOff) {
            if (shape.texture.isEmpty()) continue;
            AABB box = PrintGeometry.rotate(shape.bounds, facing());
            boolean x = box.minX == 0 && box.maxX == 1, y = box.minY == 0 && box.maxY == 1, z = box.minZ == 0 && box.maxZ == 1;
            if (switch (side) {
                case DOWN -> box.minY == 0 && x && z; case UP -> box.maxY == 1 && x && z;
                case NORTH -> box.minZ == 0 && x && y; case SOUTH -> box.maxZ == 1 && x && y;
                case WEST -> box.minX == 0 && y && z; case EAST -> box.maxX == 1 && y && z;
            }) return true;
        }
        return false;
    }
    public VoxelShape support() {
        VoxelShape shape = Shapes.empty(); double t = 1 / 4096d;
        for (Direction side : Direction.values()) if (solid(side)) shape = Shapes.or(shape, switch (side) {
            case DOWN -> Shapes.box(0, 0, 0, 1, t, 1); case UP -> Shapes.box(0, 1 - t, 0, 1, 1, 1);
            case NORTH -> Shapes.box(0, 0, 0, 1, 1, t); case SOUTH -> Shapes.box(0, 0, 1 - t, 1, 1, 1);
            case WEST -> Shapes.box(0, 0, 0, t, 1, 1); case EAST -> Shapes.box(1 - t, 0, 0, 1, 1, 1);
        });
        return shape;
    }
    public void updateGeometry() {
        off = build(false); on = build(true);
        modelData = ModelData.builder().with(MODEL, PrintGeometry.view(data, active, facing(), false)).build();
        requestModelDataUpdate();
    }
    private VoxelShape build(boolean state) {
        var shapes = state ? data.stateOn : data.stateOff;
        if (shapes.isEmpty()) return Shapes.block();
        VoxelShape result = Shapes.empty();
        for (PrintData.Shape shape : shapes) { AABB box = PrintGeometry.rotate(shape.bounds, facing()); result = Shapes.or(result, Shapes.create(box)); }
        return result.optimize();
    }
    private void changed() {
        setChanged();
        if (level == null) return;
        if (!level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            if (data.emitRedstone()) { level.updateNeighborsAt(worldPosition, getBlockState().getBlock()); for (Direction side : Direction.values()) level.updateNeighborsAt(worldPosition.relative(side), getBlockState().getBlock()); }
        }
        level.getChunkSource().getLightEngine().checkBlock(worldPosition);
    }
    @Override public void onLoad() { super.onLoad(); updateGeometry(); if (level != null && !level.isClientSide) neighborChanged(); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        data.load(tag.getCompound(tag.contains("data") ? "data" : "oc:data"));
        active = tag.getBoolean(tag.contains("state") ? "state" : "oc:state");
        updateGeometry(); if (level != null && level.isClientSide) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); level.getChunkSource().getLightEngine().checkBlock(worldPosition); }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); CompoundTag model = new CompoundTag(); data.save(model); tag.put("oc:data", model); tag.putBoolean("oc:state", active);
    }
    @Override public ModelData getModelData() { return modelData; }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); saveAdditional(tag, registries); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
