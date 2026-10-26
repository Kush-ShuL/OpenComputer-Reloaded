package li.cil.oc.server.machine;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.util.ItemUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original OC Lua argument coercions, including saturated integer conversion. */
public final class ArgumentsImpl implements Arguments {
    private final Object[] args;
    public ArgumentsImpl(Object... args) { this.args = args.clone(); }
    public int count() { return args.length; }
    public Iterator<Object> iterator() { return Arrays.asList(args).iterator(); }
    private boolean defined(int i) { return i >= 0 && i < args.length && args[i] != null; }
    private Object checked(int i, String expected) {
        if (i < 0) throw new IndexOutOfBoundsException(i);
        if (i >= args.length) throw new IllegalArgumentException("bad arguments #" + (i+1) + " (" + expected + " expected, got no value)");
        return args[i];
    }
    private IllegalArgumentException typeError(int i, String expected, Object value) {
        String type = value == null ? "nil" : value instanceof Boolean ? "boolean" : value instanceof Number ? "number" : value instanceof String || value instanceof byte[] ? "string" : value instanceof Map ? "table" : value.getClass().getSimpleName();
        return new IllegalArgumentException("bad argument #" + (i+1) + " (" + expected + " expected, got " + type + ")");
    }
    public Object checkAny(int i) { return checked(i, "value"); }
    public boolean checkBoolean(int i) { Object v=checked(i,"boolean"); if(v instanceof Boolean b)return b;throw typeError(i,"boolean",v); }
    private Number number(int i, String expected) { Object v=checked(i,expected);if(v instanceof Number n)return n;throw typeError(i,expected,v); }
    public double checkDouble(int i) { return number(i,"number").doubleValue(); }
    public int checkInteger(int i) {
        Number n=number(i,"integer");
        if(n instanceof Double || n instanceof Float) { double v=n.doubleValue();if(Double.isNaN(v))throw new IllegalArgumentException("bad argument #"+(i+1)+" (number has no integer representation)");return (int)v; }
        long v=n.longValue();return (int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,v));
    }
    public long checkLong(int i) {
        Number n=number(i,"integer");
        if((n instanceof Double || n instanceof Float) && Double.isNaN(n.doubleValue()))throw new IllegalArgumentException("bad argument #"+(i+1)+" (number has no integer representation)");
        return n.longValue();
    }
    public String checkString(int i) { Object v=checked(i,"string");if(v instanceof String s)return s;if(v instanceof byte[] b)return new String(b,StandardCharsets.UTF_8);throw typeError(i,"string",v); }
    public byte[] checkByteArray(int i) { Object v=checked(i,"string");if(v instanceof byte[] b)return b;if(v instanceof String s)return s.getBytes(StandardCharsets.UTF_8);throw typeError(i,"string",v); }
    public Map checkTable(int i) { Object v=checked(i,"table");if(v instanceof Map m)return m;throw typeError(i,"table",v); }
    public ItemStack checkItemStack(int i) {
        Map value=checkTable(i);Object raw=value.get("name");
        String name=raw instanceof byte[] b?new String(b,StandardCharsets.UTF_8):raw instanceof String s?s:null;
        ResourceLocation id=name==null?null:ResourceLocation.tryParse(name);
        if(id==null || !BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("invalid item stack");
        ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(id));
        if(stack.isDamageableItem() && value.get("damage") instanceof Number n)stack.setDamageValue(n.intValue());
        Object tag=value.get("tag");
        try {
            if(tag instanceof byte[] b)stack.set(DataComponents.CUSTOM_DATA,CustomData.of(ItemUtils.loadTag(b)));
            else if(tag instanceof String s)stack.set(DataComponents.CUSTOM_DATA,CustomData.of(ItemUtils.loadTag(s.getBytes(StandardCharsets.UTF_8))));
        } catch(java.io.IOException e) { throw new IllegalArgumentException("invalid item stack tag",e); }
        return stack;
    }
    public Object optAny(int i,Object d){return defined(i)?checkAny(i):d;}
    public boolean optBoolean(int i,boolean d){return defined(i)?checkBoolean(i):d;}
    public int optInteger(int i,int d){return defined(i)?checkInteger(i):d;}
    public long optLong(int i,long d){return defined(i)?checkLong(i):d;}
    public double optDouble(int i,double d){return defined(i)?checkDouble(i):d;}
    public String optString(int i,String d){return defined(i)?checkString(i):d;}
    public byte[] optByteArray(int i,byte[] d){return defined(i)?checkByteArray(i):d;}
    public Map optTable(int i,Map d){return defined(i)?checkTable(i):d;}
    public ItemStack optItemStack(int i,ItemStack d){return defined(i)?checkItemStack(i):d;}
    public boolean isBoolean(int i){return defined(i)&&args[i] instanceof Boolean;}
    public boolean isDouble(int i){return defined(i)&&args[i] instanceof Number;}
    public boolean isInteger(int i){return isDouble(i)&&!Double.isNaN(((Number)args[i]).doubleValue());}
    public boolean isLong(int i){return isInteger(i);}
    public boolean isString(int i){return defined(i)&&(args[i] instanceof String||args[i] instanceof byte[]);}
    public boolean isByteArray(int i){return isString(i);}
    public boolean isTable(int i){return defined(i)&&args[i] instanceof Map;}
    public boolean isItemStack(int i){return isTable(i)&&(checkTable(i).get("name") instanceof String||checkTable(i).get("name") instanceof byte[]);}
    public Object[] toArray(){Object[] copy=args.clone();for(int i=0;i<copy.length;i++)if(copy[i] instanceof byte[] b)copy[i]=new String(b,StandardCharsets.UTF_8);return copy;}
}
