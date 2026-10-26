package li.cil.oc.server.machine.luac;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.network.*;
import li.cil.repack.com.naef.jnlua.LuaState;
import java.util.*;
final class ComponentAPI extends NativeLuaAPI {
    ComponentAPI(NativeLuaArchitecture owner){super(owner);}
    @FunctionalInterface private interface Query{int apply(Component component)throws Exception;}
    private int withComponent(String address,Query query)throws Exception{Node node=machine.node(),target=node.network()==null?null:node.network().node(address);if(target instanceof Component component&&(target==node||component.canBeSeenFrom(node)))return query.apply(component);lua().pushNil();lua().pushString("no such component");return 2;}
    void initialize(){
        lua().newTable();
        bind("list",lua->{String filter=lua.isString(1)?lua.toString(1):null;boolean exact=lua.toBoolean(2);Map<String,String> result=new LinkedHashMap<>();var components=machine.components();synchronized(components){components.forEach((address,name)->{if(filter==null||(exact?name.equals(filter):name.contains(filter)))result.put(address,name);});}NativeValueBridge.push(lua,result);return 1;});
        bind("type",lua->{String name=machine.components().get(lua.checkString(1));if(name==null){lua.pushNil();lua.pushString("no such component");return 2;}lua.pushString(name);return 1;});
        bind("slot",lua->{String address=lua.checkString(1);if(!machine.components().containsKey(address)){lua.pushNil();lua.pushString("no such component");return 2;}lua.pushInteger(machine.host().componentSlot(address));return 1;});
        bind("methods",lua->withComponent(lua.checkString(1),component->{Map<String,Object> methods=new LinkedHashMap<>();machine.methods(component.host()).forEach((name,annotation)->methods.put(name,Map.of("direct",annotation.direct(),"getter",annotation.getter(),"setter",annotation.setter())));NativeValueBridge.push(lua,methods);return 1;}));
        bind("invoke",lua->{String address=lua.checkString(1),method=lua.checkString(2);Object[] arguments=NativeValueBridge.toJavaArgs(lua,3);return owner.invoke(()->machine.invoke(address,method,arguments));});
        bind("doc",lua->withComponent(lua.checkString(1),component->{String method=lua.checkString(2);return owner.documentation(()->{Callback annotation=machine.methods(component.host()).get(method);return annotation==null?null:annotation.doc();});}));
        lua().setGlobal("component");
    }
}
