package li.cil.oc.common.capabilities;

import li.cil.oc.Settings;
import li.cil.oc.common.item.FunctionalUpgradeItem;
import li.cil.oc.common.item.data.NodeData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Original Chargeable.Provider FE bridge, including battery extraction and simulation. */
public final class BatteryEnergyStorage implements IEnergyStorage {
    private final ItemStack stack;
    private final FunctionalUpgradeItem item;
    private BatteryEnergyStorage(ItemStack stack){
        this.stack=stack;this.item=(FunctionalUpgradeItem)stack.getItem();
    }

    /** Register only the three real battery items, after item registries have been populated. */
    public static void register(RegisterCapabilitiesEvent event){
        Item[] batteries=new Item[3];
        for(int tier=0;tier<3;tier++)batteries[tier]=li.cil.oc.api.Items.get("batteryupgrade"+(tier+1)).item();
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM,
                (stack,context)->ExternalPower.expose(()->new BatteryEnergyStorage(stack)),batteries);
    }

    private static double ratio(){return Settings.get().ratioForgeEnergy;}
    private static boolean validRatio(){return Double.isFinite(ratio())&&ratio()>0;}
    private int storedInForgeEnergy(double amount){
        return validRatio()?(int)Math.clamp(amount*ratio(),0,Integer.MAX_VALUE):0;
    }
    private int receiveSigned(int amount,boolean simulate){
        synchronized(stack){
            // charge returns the unused OC amount; FE expects the amount used, not the remainder.
            double unused=item.charge(stack,amount/ratio(),simulate);
            return amount-(int)(unused*ratio());
        }
    }
    @Override public int receiveEnergy(int maximum,boolean simulate){
        return maximum>0&&validRatio()?Math.clamp(receiveSigned(maximum,simulate),0,maximum):0;
    }
    @Override public int extractEnergy(int maximum,boolean simulate){
        return maximum>0&&validRatio()?Math.clamp(-receiveSigned(-maximum,simulate),0,maximum):0;
    }
    @Override public int getEnergyStored(){
        synchronized(stack){return storedInForgeEnergy(new NodeData(stack).buffer.orElse(0d));}
    }
    @Override public int getMaxEnergyStored(){return storedInForgeEnergy(Settings.get().bufferCapacitorUpgrades[item.tier()]);}
    @Override public boolean canExtract(){return validRatio();}
    @Override public boolean canReceive(){return validRatio()&&item.canCharge(stack);}
}
