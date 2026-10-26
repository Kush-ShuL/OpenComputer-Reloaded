package li.cil.oc.server.machine.luaj;

import li.cil.oc.OpenComputers;
import li.cil.oc.api.machine.Value;
import li.cil.oc.server.driver.Registry;
import li.cil.oc.server.machine.ArgumentsImpl;
import li.cil.oc.server.machine.ArchitectureCallbackContext;
import li.cil.repack.org.luaj.vm2.*;

final class UserdataAPI extends LuaJAPI {
    UserdataAPI(LuaJLuaArchitecture owner){super(owner);}
    private static Value value(Varargs args){return(Value)args.checkuserdata(1,Value.class);}
    void initialize(){
        LuaTable userdata=LuaValue.tableOf();
        userdata.set("apply",LuaValueBridge.function(args->{Value value=value(args);Object[] parameters=LuaValueBridge.toJava(args,2);return owner.invoke(()->Registry.convert(new Object[]{value.apply(machine,new ArgumentsImpl(parameters))},machine.host().world()==null?null:machine.host().world().registryAccess()));}));
        userdata.set("unapply",LuaValueBridge.function(args->{Value value=value(args);Object[] parameters=LuaValueBridge.toJava(args,2);return owner.invoke(()->{value.unapply(machine,new ArgumentsImpl(parameters));return null;});}));
        userdata.set("call",LuaValueBridge.function(args->{Value value=value(args);Object[] parameters=LuaValueBridge.toJava(args,2);return owner.invoke(()->Registry.convert(value.call(machine,new ArgumentsImpl(parameters)),machine.host().world()==null?null:machine.host().world().registryAccess()));}));
        userdata.set("dispose",LuaValueBridge.function(args->{Value value=value(args);try{ArchitectureCallbackContext.call(machine,()->{value.dispose(machine);return null;});}catch(Throwable error){OpenComputers.LOGGER.warn("Error disposing userdata {}",value.getClass().getName(),error);}return LuaValue.NIL;}));
        userdata.set("methods",LuaValueBridge.function(args->{LuaTable methods=LuaValue.tableOf();machine.methods(value(args)).forEach((name,annotation)->methods.set(name,LuaValue.valueOf(annotation.direct())));return methods;}));
        userdata.set("invoke",LuaValueBridge.function(args->{Value value=value(args);String method=args.checkjstring(2);Object[] parameters=LuaValueBridge.toJava(args,3);return owner.invoke(()->machine.invoke(value,method,parameters));}));
        userdata.set("doc",LuaValueBridge.function(args->{Value value=value(args);String method=args.checkjstring(2);return owner.documentation(()->{var annotation=machine.methods(value).get(method);if(annotation==null)throw new NoSuchMethodException();return annotation.doc();});}));
        lua().set("userdata",userdata);
    }
}
