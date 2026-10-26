package li.cil.oc.common.nanomachines;
import li.cil.oc.Settings;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
public final class NanomachineData {
    public String uuid="";public CompoundTag configuration;
    public NanomachineData(){}
    public NanomachineData(ItemStack stack){CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();uuid=tag.getString(Settings.namespace+"uuid");if(tag.contains(Settings.namespace+"configuration"))configuration=tag.getCompound(Settings.namespace+"configuration");}
    public NanomachineData(ControllerImpl controller){uuid=controller.uuid;configuration=new CompoundTag();controller.configuration.save(configuration,true);}
    public void save(ItemStack stack){CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putString(Settings.namespace+"uuid",uuid);if(configuration==null)tag.remove(Settings.namespace+"configuration");else tag.put(Settings.namespace+"configuration",configuration.copy());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
}
