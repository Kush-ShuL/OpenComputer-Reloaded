package li.cil.oc.server.machine.luac;

import li.cil.oc.Settings;
import li.cil.repack.com.naef.jnlua.LuaState;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** ERIS permanent values and portable C callback names, retained across coroutine snapshots. */
final class PersistenceAPI extends NativeLuaAPI {
    private String persistKey="__persist"+UUID.randomUUID().toString().replace("-","");
    PersistenceAPI(NativeLuaArchitecture owner){super(owner);}
    void initialize(){
        LuaState lua=lua();lua.pushJavaFunction(state->{state.pushString(persistKey);return 1;});lua.setGlobal("persistKey");
        if(!Settings.get().allowPersistence)return;
        lua.newTable();lua.newTable();int perms=lua.getTop()-1,uperms=lua.getTop();lua.pushString("_G");lua.getGlobal("_G");flatten(lua,perms,uperms);lua.setField(lua.getRegistryIndex(),"uperms");lua.setField(lua.getRegistryIndex(),"perms");
    }
    private void flatten(LuaState lua,int perms,int uperms){
        if(lua.isFunction(-1)||lua.isTable(-1)){
            lua.pushValue(-2);lua.getTable(uperms);if(!lua.isNil(-1))throw new IllegalStateException("duplicate permanent value "+lua.toString(-3));lua.pop(1);
            lua.pushValue(-1);lua.getTable(perms);boolean fresh=lua.isNil(-1);lua.pop(1);
            if(fresh){
                lua.pushValue(-1);lua.pushValue(-3);lua.rawSet(perms);lua.pushValue(-2);lua.pushValue(-2);lua.rawSet(uperms);
                if(lua.isTable(-1)){
                    String key=lua.toString(-2);List<String> children=new ArrayList<>();lua.pushNil();while(lua.next(-2)){lua.pop(1);children.add(lua.toString(-1));}Collections.sort(children);
                    for(String child:children){lua.pushString(key+"."+child);lua.getField(-2,child);flatten(lua,perms,uperms);}
                }
            }
        }
        lua.pop(2);
    }
    void load(CompoundTag nbt){if(nbt.contains("persistKey"))persistKey=nbt.getString("persistKey");}
    void save(CompoundTag nbt){nbt.putString("persistKey",persistKey);}
    private void configure(){LuaState lua=lua();lua.getGlobal("eris");lua.getField(-1,"settings");lua.pushString("spkey");lua.pushString(persistKey);lua.call(2,0);lua.getField(-1,"settings");lua.pushString("path");lua.pushBoolean(Settings.get().debugPersistence);lua.call(2,0);lua.pop(1);}
    byte[] persist(int index){
        if(!Settings.get().allowPersistence)return new byte[0];LuaState lua=lua();configure();int top=lua.getTop();
        try{lua.gc(LuaState.GcAction.STOP,0);lua.getGlobal("eris");lua.getField(-1,"persist");if(!lua.isFunction(-1))return new byte[0];lua.getField(lua.getRegistryIndex(),"perms");lua.pushValue(index);lua.call(2,1);return lua.isString(-1)?lua.toByteArray(-1):new byte[0];}
        finally{lua.setTop(top);lua.gc(LuaState.GcAction.RESTART,0);}
    }
    boolean unpersist(byte[] data){
        if(!Settings.get().allowPersistence)return false;LuaState lua=lua();configure();int top=lua.getTop();
        try{lua.gc(LuaState.GcAction.STOP,0);lua.getGlobal("eris");lua.getField(-1,"unpersist");if(!lua.isFunction(-1)){lua.setTop(top);return false;}lua.getField(lua.getRegistryIndex(),"uperms");lua.pushByteArray(data);lua.call(2,1);lua.insert(-2);lua.pop(1);return true;}
        catch(Throwable error){lua.setTop(top);throw error;}
        finally{lua.gc(LuaState.GcAction.RESTART,0);}
    }
}
