package li.cil.oc.server.component;

import li.cil.oc.api.internal.Database;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import com.google.common.hash.Hashing;
import li.cil.oc.Settings;
import li.cil.oc.util.ItemUtils;
import java.io.IOException;
import java.util.*;

public final class UpgradeDatabase extends UpgradeBase implements Database {
    private final ItemStack[] entries;
    private final ItemStack container;
    private HolderLookup.Provider registries;
    public UpgradeDatabase(EnvironmentHost host,int tier,ItemStack container){
        super(host,"database","Object catalogue","iCatalogue (patent pending)",false);
        this.container=container;
        entries=new ItemStack[Settings.get().databaseEntriesPerTier[tier]];
        registries=host.world()==null?RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY):host.world().registryAccess();
        loadEntries(container.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("oc:data"));
        ((Component)node()).setVisibility(Visibility.Network);
    }
    @Override public Map<String,String> getDeviceInfo(){var info=new LinkedHashMap<>(super.getDeviceInfo());info.put("capacity",Integer.toString(size()));return info;}
    public int size(){return entries.length;}
    public ItemStack getStackInSlot(int slot){return slot<0||slot>=size()?ItemStack.EMPTY:entries[slot].copy();}
    public void setStackInSlot(int slot,ItemStack stack){
        if(slot<0||slot>=size())return;
        ItemStack value=stack==null?ItemStack.EMPTY:stack;
        if(value==entries[slot]||value.isEmpty()&&entries[slot].isEmpty())return;
        entries[slot]=ItemStack.EMPTY;
        if(!value.isEmpty()){
            if(value.getCount()>1)value.setCount(1);
            entries[slot]=value;
        }
        CompoundTag root=container.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag(),data=root.getCompound("oc:data");
        saveEntries(data);root.put("oc:data",data);container.set(DataComponents.CUSTOM_DATA,CustomData.of(root));changed();
    }
    public int findStackWithHash(String hash){for(int i=0;i<entries.length;i++)if(hash(entries[i]).equals(hash))return i;return -1;}
    private String hash(ItemStack stack){try{return Hashing.sha256().hashBytes(ItemUtils.saveStack(stack,registries)).toString();}catch(IOException error){throw new IllegalStateException("cannot hash item stack",error);}}
    private Database database(String address){Node target=node().network()==null?null:node().network().node(address);if(!(target instanceof Component))throw new IllegalArgumentException("no such component");if(!(target.host() instanceof UpgradeDatabase database))throw new IllegalArgumentException("not a database");return database;}
    @Callback(doc="function(slot:number):table -- Get the representation of the item stack stored in the specified slot.")
    public Object[] get(Context context,Arguments args){return new Object[]{getStackInSlot(slot(args,0,size()))};}
    @Callback(doc="function(slot:number):string -- Computes a hash value for the item stack in the specified slot.")
    public Object[] computeHash(Context context,Arguments args){return new Object[]{hash(entries[slot(args,0,size())])};}
    @Callback(doc="function(hash:string):number -- Get the index of an item stack with the specified hash. Returns a negative value if no such stack was found.")
    public Object[] indexOf(Context context,Arguments args){int index=findStackWithHash(args.checkString(0));return new Object[]{index<0?-1:index+1};}
    @Callback(doc="function(slot:number):boolean -- Clears the specified slot. Returns true if there was something in the slot before.")
    public Object[] clear(Context context,Arguments args){int index=slot(args,0,size());boolean old=!entries[index].isEmpty();setStackInSlot(index,ItemStack.EMPTY);return new Object[]{old};}
    @Callback(doc="function(fromSlot:number, toSlot:number[, address:string]):boolean -- Copies an entry to another slot, optionally to another database. Returns true if something was overwritten.")
    public Object[] copy(Context context,Arguments args){int from=slot(args,0,size());Database target=args.count()>2?database(args.checkString(2)):this;int to=slot(args,1,target.size());ItemStack old=target.getStackInSlot(to);target.setStackInSlot(to,entries[from].copy());return new Object[]{!old.isEmpty()};}
    @Callback(doc="function(address:string):number -- Copies the data stored in this database to another database with the specified address.")
    public Object[] clone(Context context,Arguments args){Database target=database(args.checkString(0));int count=Math.min(size(),target.size());for(int i=0;i<count;i++)target.setStackInSlot(i,entries[i].copy());context.pause(.25);return new Object[]{count};}
    private void loadEntries(CompoundTag tag){Arrays.fill(entries,ItemStack.EMPTY);ListTag list=tag.getList("oc:items",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(list.size(),entries.length);i++)entries[i]=ItemStack.parseOptional(registries,list.getCompound(i));}
    private void saveEntries(CompoundTag tag){ListTag list=new ListTag();for(ItemStack entry:entries)list.add(entry.saveOptional(registries));tag.put("oc:items",list);}
    @Override public void load(CompoundTag tag){super.load(tag);loadEntries(tag);}
    @Override public void save(CompoundTag tag){super.save(tag);saveEntries(tag);}
    @Override public void load(CompoundTag tag,HolderLookup.Provider registries){this.registries=registries;load(tag);}
    @Override public void save(CompoundTag tag,HolderLookup.Provider registries){this.registries=registries;save(tag);}
}
