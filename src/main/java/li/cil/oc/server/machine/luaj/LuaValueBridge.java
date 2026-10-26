package li.cil.oc.server.machine.luaj;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.machine.Value;
import li.cil.repack.org.luaj.vm2.*;
import li.cil.repack.org.luaj.vm2.lib.VarArgFunction;
import java.lang.reflect.Array;
import java.util.*;

/** Java counterpart of the original ScalaClosure conversions, including binary Lua strings. */
final class LuaValueBridge {
    @FunctionalInterface interface Function { Varargs apply(Varargs arguments)throws Exception; }
    static VarArgFunction function(Function function){return new VarArgFunction(){public Varargs invoke(Varargs args){try{return function.apply(args);}catch(LuaError error){throw error;}catch(Exception error){throw new LuaError(error);}}};}
    static LuaValue toLua(Object value){return toLua(value,new IdentityHashMap<>());}
    private static LuaValue toLua(Object value,IdentityHashMap<Object,LuaValue> memo){
        if(value==null)return LuaValue.NIL;
        if(value instanceof Boolean flag)return LuaValue.valueOf(flag);
        if(value instanceof Number number)return LuaValue.valueOf(number.doubleValue());
        if(value instanceof Character character)return LuaValue.valueOf(character.toString());
        if(value instanceof String string)return LuaValue.valueOf(string);
        if(value instanceof byte[] bytes)return LuaValue.valueOf(bytes);
        if(value instanceof Value userdata&&Settings.get().allowUserdata)return LuaValue.userdataOf(userdata);
        LuaValue previous=memo.get(value);if(previous!=null)return previous;
        LuaTable table=LuaValue.tableOf();
        if(value instanceof Map<?,?> map){memo.put(value,table);for(var entry:map.entrySet()){LuaValue key=toLua(entry.getKey(),memo);if(!key.isnil())table.set(key,toLua(entry.getValue(),memo));}return table;}
        if(value.getClass().isArray()){memo.put(value,table);for(int index=0;index<Array.getLength(value);index++)table.set(index+1,toLua(Array.get(value,index),memo));return table;}
        if(value instanceof Iterable<?> iterable){memo.put(value,table);int index=1;for(Object element:iterable)table.set(index++,toLua(element,memo));return table;}
        OpenComputers.LOGGER.warn("Tried to push unsupported value {} to Lua",value.getClass().getName());return LuaValue.NIL;
    }
    static Object toJava(LuaValue value){return toJava(value,new IdentityHashMap<>());}
    private static Object toJava(LuaValue value,IdentityHashMap<LuaValue,Object> memo){
        return switch(value.type()){
            case LuaValue.TBOOLEAN->value.toboolean();
            case LuaValue.TNUMBER->value.todouble();
            case LuaValue.TSTRING->{if(value instanceof LuaString string)yield Arrays.copyOfRange(string.m_bytes,string.m_offset,string.m_offset+string.m_length);yield value.tojstring();}
            case LuaValue.TUSERDATA->value.touserdata();
            case LuaValue.TTABLE->{Object previous=memo.get(value);if(previous!=null)yield previous;Map<Object,Object> table=new LinkedHashMap<>();memo.put(value,table);for(LuaValue key:value.checktable().keys())table.put(toJava(key,memo),toJava(value.get(key),memo));yield table;}
            default->null;
        };
    }
    static Object[] toJava(Varargs args,int start){Object[] values=new Object[Math.max(0,args.narg()-start+1)];IdentityHashMap<LuaValue,Object> memo=new IdentityHashMap<>();for(int index=0;index<values.length;index++)values[index]=toJava(args.arg(start+index),memo);return values;}
}
