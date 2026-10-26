package li.cil.oc.server.component;

import li.cil.oc.api.internal.Agent;
import li.cil.oc.api.network.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Persistent fluid storage exposed through the agent's MultiTank. */
public final class UpgradeTank extends UpgradeBase implements IFluidTank {
    private final FluidTank tank=new FluidTank(16000);
    public UpgradeTank(EnvironmentHost host){super(host,null,"Tank upgrade","FluidStore",false);setNode(li.cil.oc.api.Network.newNode(this,Visibility.None).create());}
    public FluidStack getFluid(){return tank.getFluid();}
    public int getFluidAmount(){return tank.getFluidAmount();}
    public int getCapacity(){return tank.getCapacity();}
    public boolean isFluidValid(FluidStack stack){return tank.isFluidValid(stack);}
    public int fill(FluidStack stack,IFluidHandler.FluidAction action){int amount=tank.fill(stack,action);if(action.execute()&&amount>0)notifyChange(amount);return amount;}
    public FluidStack drain(int amount,IFluidHandler.FluidAction action){FluidStack drained=tank.drain(amount,action);if(action.execute()&&!drained.isEmpty())notifyChange(-drained.getAmount());return drained;}
    public FluidStack drain(FluidStack stack,IFluidHandler.FluidAction action){FluidStack drained=tank.drain(stack,action);if(action.execute()&&!drained.isEmpty())notifyChange(-drained.getAmount());return drained;}
    private void notifyChange(int amount){changed();int index=0;if(host instanceof Agent agent)for(int i=0;i<agent.tank().tankCount();i++)if(agent.tank().getFluidTank(i)==this){index=i;break;}node().sendToVisible("computer.signal","tank_changed",index+1,amount);}
    @Override public void load(CompoundTag tag,HolderLookup.Provider registries){super.load(tag);tank.readFromNBT(registries,tag.getCompound("tank"));}
    @Override public void save(CompoundTag tag,HolderLookup.Provider registries){super.save(tag);tag.put("tank",tank.writeToNBT(registries,new CompoundTag()));}
}
