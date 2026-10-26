package li.cil.oc.server.machine.luac;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.machine.Value;
import li.cil.repack.com.naef.jnlua.*;
import java.lang.reflect.Array;
import java.util.*;

final class NativeValueBridge {
    @FunctionalInterface interface Function { int invoke(LuaState state)throws Exception; }
    static JavaFunction function(Function function){return state->{try{return function.invoke(state);}catch(Exception error){throw raise(error);}};}
    @SuppressWarnings("unchecked") static <E extends Throwable> RuntimeException raise(Throwable error)throws E{throw(E)error;}
    static void push(LuaState lua,Object value){Map<Object,Integer> memo=new IdentityHashMap<>();try{push(lua,value,memo);}finally{for(int reference:memo.values())lua.unref(lua.getRegistryIndex(),reference);}}
    private static void push(LuaState lua,Object value,Map<Object,Integer> memo){
        if(value==null){lua.pushNil();return;}
        if(value instanceof Boolean flag){lua.pushBoolean(flag);return;}
        if(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long){lua.pushInteger(((Number)value).longValue());return;}
        if(value instanceof Number number){lua.pushNumber(number.doubleValue());return;}
        if(value instanceof Character character){lua.pushString(character.toString());return;}
        if(value instanceof String string){lua.pushString(string);return;}
        if(value instanceof byte[] bytes){lua.pushByteArray(bytes);return;}
        if(value instanceof Value userdata&&Settings.get().allowUserdata){lua.pushJavaObjectRaw(userdata);return;}
        Integer previous=memo.get(value);if(previous!=null){lua.rawGet(lua.getRegistryIndex(),previous);return;}
        if(value instanceof Map<?,?> map){lua.newTable(0,map.size());int table=lua.getTop();lua.pushValue(table);memo.put(value,lua.ref(lua.getRegistryIndex()));for(var entry:map.entrySet()){if(entry.getKey()==null)continue;push(lua,entry.getKey(),memo);push(lua,entry.getValue(),memo);lua.rawSet(table);}return;}
        if(value.getClass().isArray()){lua.newTable();int table=lua.getTop();lua.pushValue(table);memo.put(value,lua.ref(lua.getRegistryIndex()));for(int index=0;index<Array.getLength(value);index++){push(lua,Array.get(value,index),memo);lua.rawSet(table,index+1);}return;}
        if(value instanceof Iterable<?> iterable){lua.newTable();int table=lua.getTop();lua.pushValue(table);memo.put(value,lua.ref(lua.getRegistryIndex()));int index=1;for(Object element:iterable){push(lua,element,memo);lua.rawSet(table,index++);}return;}
        OpenComputers.LOGGER.warn("Tried to push unsupported value {} to Lua",value.getClass().getName());lua.pushNil();
    }
    static Object toJava(LuaState lua,int index){return switch(lua.type(index)){case BOOLEAN->lua.toBoolean(index);case NUMBER->{if(lua.isInteger(index))yield Long.valueOf(lua.toInteger(index));yield Double.valueOf(lua.toNumber(index));}case STRING->lua.toByteArray(index);case TABLE->lua.toJavaObject(index,Map.class);case USERDATA->lua.toJavaObjectRaw(index);default->null;};}
    static Object[] toJavaArgs(LuaState lua,int start){Object[] values=new Object[Math.max(0,lua.getTop()-start+1)];for(int index=0;index<values.length;index++)values[index]=toJava(lua,start+index);return values;}
}
