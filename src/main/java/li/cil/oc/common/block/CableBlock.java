package li.cil.oc.common.block;

import li.cil.oc.common.block.entity.CableBlockEntity;
import li.cil.oc.util.ItemUtils;
import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.*;
import java.util.*;
import java.util.function.Supplier;

public final class CableBlock extends Block implements EntityBlock {
    public static final BooleanProperty[] CONNECTIONS=Arrays.stream(Direction.values()).map(side->BooleanProperty.create(side.getName())).toArray(BooleanProperty[]::new);
    public static final BooleanProperty[] CABLE_NEIGHBORS=Arrays.stream(Direction.values()).map(side->BooleanProperty.create(side.getName()+"_cable")).toArray(BooleanProperty[]::new);
    private static final VoxelShape[] SHAPES=new VoxelShape[64];
    private final Supplier<BlockEntityType<CableBlockEntity>> type;
    static{
        VoxelShape center=Block.box(6,6,6,10,10,10);
        VoxelShape[] parts={Block.box(6,0,6,10,6,10),Block.box(6,10,6,10,16,10),Block.box(6,6,0,10,10,6),Block.box(6,6,10,10,10,16),Block.box(0,6,6,6,10,10),Block.box(10,6,6,16,10,10)};
        for(int mask=0;mask<64;mask++){VoxelShape shape=center;for(int bit=0;bit<6;bit++)if((mask&(1<<bit))!=0)shape=Shapes.or(shape,parts[bit]);SHAPES[mask]=shape.optimize();}
    }
    public CableBlock(Properties properties,Supplier<BlockEntityType<CableBlockEntity>> type){super(properties);this.type=type;BlockState state=stateDefinition.any();for(var property:CONNECTIONS)state=state.setValue(property,false);for(var property:CABLE_NEIGHBORS)state=state.setValue(property,false);registerDefaultState(state);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(CONNECTIONS);builder.add(CABLE_NEIGHBORS);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){int mask=0;for(int bit=0;bit<6;bit++)if(state.getValue(CONNECTIONS[bit]))mask|=1<<bit;return SHAPES[mask];}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return type.get().create(pos,state);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moved){if(level.getBlockEntity(pos) instanceof CableBlockEntity cable)cable.rebuildConnections();}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(level.getBlockEntity(pos) instanceof CableBlockEntity cable){var data=ItemUtils.customData(stack);if(data.contains("oc:color"))cable.setColor(data.getInt("oc:color"));cable.rebuildConnections();}}
    private ItemStack stack(CableBlockEntity cable){ItemStack stack=new ItemStack(this);var data=ItemUtils.customData(stack);data.putInt("oc:color",cable.getColor());ItemUtils.saveCustomData(stack,data);return stack;}
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state){return level.getBlockEntity(pos) instanceof CableBlockEntity cable?stack(cable):new ItemStack(this);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CableBlockEntity cable?List.of(stack(cable)):List.of(new ItemStack(this));}
}
