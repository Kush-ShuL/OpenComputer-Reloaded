package li.cil.oc.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.*;
import net.neoforged.neoforge.fluids.capability.wrappers.BucketPickupHandlerWrapper;
import java.util.Optional;

/** Fluid transfer and live world-liquid wrappers used by transposers and robots. */
public final class FluidUtils {
    private FluidUtils(){}
    public static Optional<IFluidHandler> fluidHandlerAt(Level level,BlockPos pos,Direction side){
        if(level==null||!level.hasChunkAt(pos))return Optional.empty();IFluidHandler handler=level.getCapability(Capabilities.FluidHandler.BLOCK,pos,side);return Optional.of(handler!=null?handler:new WorldFluidHandler(level,pos.immutable()));
    }
    public static IFluidHandlerItem fluidHandlerOf(ItemStack stack){return stack==null||stack.isEmpty()?null:FluidUtil.getFluidHandler(stack).orElse(null);}
    public static int transferBetweenFluidHandlers(IFluidHandler source,IFluidHandler sink,int limit,int sourceTank){
        if(source==sink||limit<=0)return 0;
        FluidStack selected=sourceTank>=0&&sourceTank<source.getTanks()?source.getFluidInTank(sourceTank):FluidStack.EMPTY;
        // The original zero-based selector falls back to automatic draining for an empty or absent tank.
        FluidStack drained=selected.isEmpty()?source.drain(limit,IFluidHandler.FluidAction.SIMULATE):source.drain(selected.copyWithAmount(Math.min(limit,selected.getAmount())),IFluidHandler.FluidAction.SIMULATE);
        if(drained.isEmpty())return 0;int accepted=sink.fill(drained,IFluidHandler.FluidAction.SIMULATE);if(accepted<=0)return 0;FluidStack actual=source.drain(drained.copyWithAmount(accepted),IFluidHandler.FluidAction.EXECUTE);if(actual.isEmpty())return 0;
        int filled=sink.fill(actual,IFluidHandler.FluidAction.EXECUTE);if(filled<actual.getAmount())source.fill(actual.copyWithAmount(actual.getAmount()-filled),IFluidHandler.FluidAction.EXECUTE);return filled;
    }
    public static int transferBetweenFluidHandlers(IFluidHandler source,IFluidHandler sink,int limit){return transferBetweenFluidHandlers(source,sink,limit,-1);}
    public static int transferBetweenFluidHandlersAt(Level level,BlockPos source,Direction sourceSide,BlockPos sink,Direction sinkSide,int limit,int tank){IFluidHandler a=fluidHandlerAt(level,source,sourceSide).orElse(null),b=fluidHandlerAt(level,sink,sinkSide).orElse(null);return a==null||b==null?0:transferBetweenFluidHandlers(a,b,limit,tank);}
    private record WorldFluidHandler(Level level,BlockPos pos) implements IFluidHandler {
        private IFluidHandler source(){BlockState state=level.getBlockState(pos);return state.getBlock() instanceof BucketPickup pickup&&state.getFluidState().isSource()?new BucketPickupHandlerWrapper(null,pickup,level,pos):null;}
        public int getTanks(){return source()==null?0:1;}
        public FluidStack getFluidInTank(int tank){IFluidHandler source=source();return tank==0&&source!=null?source.getFluidInTank(0):FluidStack.EMPTY;}
        public int getTankCapacity(int tank){return FluidType.BUCKET_VOLUME;}
        public boolean isFluidValid(int tank,FluidStack fluid){return !fluid.isEmpty()&&fluid.getFluidType().canBePlacedInLevel(level,pos,fluid);}
        public FluidStack drain(FluidStack fluid,FluidAction action){IFluidHandler source=source();return source==null?FluidStack.EMPTY:source.drain(fluid,action);}
        public FluidStack drain(int amount,FluidAction action){IFluidHandler source=source();return source==null?FluidStack.EMPTY:source.drain(amount,action);}
        public int fill(FluidStack fluid,FluidAction action){
            if(fluid.isEmpty()||fluid.getAmount()<FluidType.BUCKET_VOLUME||!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!fluid.getFluidType().canBePlacedInLevel(level,pos,fluid))return 0;BlockState state=level.getBlockState(pos);
            // Source liquids are replaceable blocks, but their one-bucket capacity is already full.
            if(!state.getFluidState().isEmpty()&&state.getFluidState().isSource())return 0;
            boolean container=state.getBlock() instanceof LiquidBlockContainer block&&block.canPlaceLiquid(null,level,pos,state,fluid.getFluid());
            if(!container&&!state.isAir()&&!state.canBeReplaced())return 0;
            if(action.execute()){
                if(fluid.getFluidType().isVaporizedOnPlacement(level,pos,fluid))fluid.getFluidType().onVaporize(null,level,pos,fluid.copyWithAmount(FluidType.BUCKET_VOLUME));
                else if(container){if(!((LiquidBlockContainer)state.getBlock()).placeLiquid(level,pos,state,fluid.getFluidType().getStateForPlacement(level,pos,fluid)))return 0;}
                else {FluidUtil.destroyBlockOnFluidPlacement(level,pos);if(!level.setBlock(pos,fluid.getFluidType().getBlockForFluidState(level,pos,fluid.getFluid().defaultFluidState()),3))return 0;}
            }return FluidType.BUCKET_VOLUME;
        }
    }
}
