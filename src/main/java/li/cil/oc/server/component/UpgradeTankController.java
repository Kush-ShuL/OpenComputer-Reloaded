package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.util.FluidUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.*;
import java.util.*;
import java.util.function.Function;

public class UpgradeTankController extends UpgradeBase {
    public UpgradeTankController(EnvironmentHost host){super(host,"tank_controller","Tank controller","FlowCheckDX",false);}
    @Override protected Direction side(Arguments args,int index){int value=args.checkInteger(index);if(value<0||value>5||host instanceof li.cil.oc.api.internal.Robot&&value!=0&&value!=1&&value!=3)throw new IllegalArgumentException("invalid side");Direction side=Direction.from3DDataValue(value);return host instanceof li.cil.oc.api.internal.Robot robot?robot.toGlobal(side):side;}
    private Object[] tank(Arguments args,Function<IFluidHandler,Object[]> action){Direction direction=side(args,0);return FluidUtils.fluidHandlerAt(host.world(),position().relative(direction),direction.getOpposite()).map(action).orElseGet(()->new Object[]{null,"no tank"});}
    private int[] tanks(Arguments args,IFluidHandler handler){if(defined(args,1))return new int[]{slot(args,1,handler.getTanks())};int[] result=new int[handler.getTanks()];for(int i=0;i<result.length;i++)result[i]=i;return result;}
    @Callback(doc="function(side:number[,tank:number]):number -- Get stored fluid amount.") public Object[] getTankLevel(Context context,Arguments args){return tank(args,handler->{int total=0;for(int index:tanks(args,handler))total+=handler.getFluidInTank(index).getAmount();return new Object[]{total};});}
    @Callback(doc="function(side:number[,tank:number]):number -- Get tank capacity.") public Object[] getTankCapacity(Context context,Arguments args){return tank(args,handler->{int capacity=0;for(int index:tanks(args,handler))capacity=Math.max(capacity,handler.getTankCapacity(index));return new Object[]{capacity};});}
    private Map<String,Object> fluid(IFluidHandler handler,int tank){var fluid=handler.getFluidInTank(tank);Map<String,Object> result=new HashMap<>();result.put("capacity",handler.getTankCapacity(tank));result.put("amount",fluid.getAmount());if(!fluid.isEmpty()){result.put("hasTag",!fluid.getComponentsPatch().isEmpty());result.put("name",BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());result.put("label",fluid.getHoverName().getString());}return result;}
    @Callback(doc="function(side:number[,tank:number]):table -- Inspect tank contents.") public Object[] getFluidInTank(Context context,Arguments args){if(!Settings.get().allowItemStackInspection)return new Object[]{null,"not enabled in config"};return tank(args,handler->{int[] selected=tanks(args,handler);if(defined(args,1))return new Object[]{fluid(handler,selected[0])};Object[] values=new Object[selected.length];for(int i=0;i<selected.length;i++)values[i]=fluid(handler,selected[i]);return new Object[]{values};});}
    @Callback(doc="function(side:number):number -- Get the number of tanks.") public Object[] getTankCount(Context context,Arguments args){return tank(args,handler->new Object[]{handler.getTanks()});}

    public static final class Agent extends UpgradeTankController {
        private final li.cil.oc.api.internal.Agent agent;
        public Agent(EnvironmentHost host){super(host);agent=(li.cil.oc.api.internal.Agent)host;}
        private int inventorySlot(Arguments args,int index){return defined(args,index)?slot(args,index,agent.mainInventory().getContainerSize()):agent.selectedSlot();}
        private int tankSlot(Arguments args,int index){return defined(args,index)?slot(args,index,agent.tank().tankCount()):agent.selectedTank();}
        private Object[] itemFluid(Arguments args,Function<IFluidHandlerItem,Object[]> action){ItemStack stack=agent.mainInventory().getItem(inventorySlot(args,0));var handler=FluidUtils.fluidHandlerOf(stack);return handler!=null&&handler.getTanks()>0?action.apply(handler):new Object[]{null,"item is not a fluid container"};}
        @Callback public Object[] getTankLevelInSlot(Context context,Arguments args){return itemFluid(args,handler->new Object[]{handler.getFluidInTank(0).getAmount()});}
        @Callback public Object[] getTankCapacityInSlot(Context context,Arguments args){return itemFluid(args,handler->new Object[]{handler.getTankCapacity(0)});}
        @Callback public Object[] getFluidInTankInSlot(Context context,Arguments args){return Settings.get().allowItemStackInspection?itemFluid(args,handler->new Object[]{handler.getFluidInTank(0).copy()}):new Object[]{null,"not enabled in config"};}
        @Callback public Object[] getFluidInInternalTank(Context context,Arguments args){return Settings.get().allowItemStackInspection?new Object[]{agent.tank().getFluidTank(tankSlot(args,0)).getFluid().copy()}:new Object[]{null,"not enabled in config"};}
        @Callback public Object[] drain(Context context,Arguments args){return transfer(args,true);}
        @Callback public Object[] fill(Context context,Arguments args){return transfer(args,false);}
        private Object[] transfer(Arguments args,boolean into){if(agent.tank().tankCount()==0)return new Object[]{null,"no tank"};if(agent.mainInventory().getContainerSize()==0)return new Object[]{null,"nothing selected"};ItemStack stack=agent.mainInventory().getItem(agent.selectedSlot());if(stack.isEmpty())return new Object[]{null,"nothing selected"};if(stack.getCount()!=1)return new Object[]{null,"select a single fluid container"};var handler=FluidUtils.fluidHandlerOf(stack);if(handler==null)return new Object[]{null,"item is not a fluid container"};IFluidHandler own=new SelectedTank(agent.tank().getFluidTank(agent.selectedTank()));int amount=Math.max(0,args.optInteger(0,1000)),moved=into?FluidUtils.transferBetweenFluidHandlers(handler,own,amount):FluidUtils.transferBetweenFluidHandlers(own,handler,amount);if(moved<=0)return new Object[]{null,"incompatible or no fluid"};agent.mainInventory().setItem(agent.selectedSlot(),handler.getContainer());changed();return new Object[]{true,moved};}
    }
    private record SelectedTank(IFluidTank tank) implements IFluidHandler {
        public int getTanks(){return 1;}public FluidStack getFluidInTank(int index){return index==0?tank.getFluid():FluidStack.EMPTY;}public int getTankCapacity(int index){return tank.getCapacity();}public boolean isFluidValid(int index,FluidStack stack){return index==0&&tank.isFluidValid(stack);}public int fill(FluidStack stack,FluidAction action){return tank.fill(stack,action);}public FluidStack drain(FluidStack stack,FluidAction action){return tank.drain(stack,action);}public FluidStack drain(int count,FluidAction action){return tank.drain(count,action);}
    }
}
