package li.cil.oc.server.component;

import li.cil.oc.api.internal.Database;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.*;

public final class UpgradeDatabase extends UpgradeBase implements Database {
    private final ItemStack[] entries;
    public UpgradeDatabase(EnvironmentHost host,int tier){super(host,"database","Object catalogue","iCatalogue (patent pending)",false);entries=new ItemStack[new int[]{9,25,81}[tier]];Arrays.fill(entries,ItemStack.EMPTY);}
    public int size(){return entries.length;}
    public ItemStack getStackInSlot(int slot){return entries[slot].copy();}
    public void setStackInSlot(int slot,ItemStack stack){entries[slot]=stack==null?ItemStack.EMPTY:stack.copy();changed();}
    public int findStackWithHash(String hash){for(int i=0;i<entries.length;i++)if(!entries[i].isEmpty()&&hash(entries[i]).equals(hash))return i;return -1;}
    private String hash(ItemStack stack){try{var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed((CompoundTag)stack.save(host.world().registryAccess()),bytes);return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));}catch(Exception error){throw new IllegalStateException("cannot hash item stack",error);}}
    private Database database(String address){Node target=node().network()==null?null:node().network().node(address);if(target==null||!target.canBeReachedFrom(node()))throw new IllegalArgumentException("no such component");if(!(target.host() instanceof Database database))throw new IllegalArgumentException("not a database");return database;}
    @Callback public Object[] get(Context context,Arguments args){return new Object[]{getStackInSlot(slot(args,0,size()))};}
    @Callback public Object[] computeHash(Context context,Arguments args){ItemStack stack=entries[slot(args,0,size())];return stack.isEmpty()?new Object[]{null}:new Object[]{hash(stack)};}
    @Callback public Object[] indexOf(Context context,Arguments args){int index=findStackWithHash(args.checkString(0));return new Object[]{index<0?-1:index+1};}
    @Callback public Object[] clear(Context context,Arguments args){int index=slot(args,0,size());boolean old=!entries[index].isEmpty();setStackInSlot(index,ItemStack.EMPTY);return new Object[]{old};}
    @Callback public Object[] copy(Context context,Arguments args){int from=slot(args,0,size());Database target=defined(args,2)?database(args.checkString(2)):this;int to=slot(args,1,target.size());ItemStack old=target.getStackInSlot(to);target.setStackInSlot(to,entries[from]);return new Object[]{old!=null&&!old.isEmpty()};}
    @Callback public Object[] clone(Context context,Arguments args){Database target=database(args.checkString(0));int count=Math.min(size(),target.size());for(int i=0;i<count;i++)target.setStackInSlot(i,entries[i]);context.pause(.25);return new Object[]{count};}
    @Override public void load(CompoundTag tag,HolderLookup.Provider registries){super.load(tag);Arrays.fill(entries,ItemStack.EMPTY);ListTag list=tag.getList("oc:items",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(list.size(),entries.length);i++)entries[i]=ItemStack.parseOptional(registries,list.getCompound(i));}
    @Override public void save(CompoundTag tag,HolderLookup.Provider registries){super.save(tag);ListTag list=new ListTag();for(ItemStack entry:entries)list.add(entry.saveOptional(registries));tag.put("oc:items",list);}
}
