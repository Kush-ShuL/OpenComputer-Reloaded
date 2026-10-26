package li.cil.oc.common.integration.computercraft;

import dan200.computercraft.api.lua.IDynamicLuaObject;
import java.util.Map;
import li.cil.oc.api.driver.Converter;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.ManagedPeripheral;
import li.cil.oc.api.prefab.AbstractValue;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;

/** The original callable Lua userdata conversion; restored external userdata remains unavailable. */
public final class LuaObjectConverter implements Converter {
    @Override public void convert(Object value,Map<Object,Object> output){convert(value,output,null);}
    @Override public void convert(Object value,Map<Object,Object> output,HolderLookup.Provider registries){
        LuaMethods methods=new LuaMethods(value);if(value instanceof IDynamicLuaObject||!methods.empty())output.put("value",new LuaObjectValue(value,methods));
    }
    public static final class LuaObjectValue extends AbstractValue implements ManagedPeripheral {
        private Object value;
        private LuaMethods methods;
        public LuaObjectValue(){}
        private LuaObjectValue(Object value,LuaMethods methods){this.value=value;this.methods=methods;}
        @Override public String[] methods(){return methods==null?new String[0]:methods.names();}
        @Override public Object[] invoke(String method,Context context,Arguments args)throws Exception{
            if(value==null)return new Object[]{null,"ComputerCraft userdata cannot be persisted"};
            ServerLevel level=context instanceof li.cil.oc.api.machine.Machine machine&&machine.host().world() instanceof ServerLevel server?server:null;
            return methods.invoke(method,null,LuaMethods.arguments(args),level);
        }
    }
}
