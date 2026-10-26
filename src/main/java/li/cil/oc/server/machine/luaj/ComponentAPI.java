package li.cil.oc.server.machine.luaj;

import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Node;
import li.cil.repack.org.luaj.vm2.*;

final class ComponentAPI extends LuaJAPI {
    ComponentAPI(LuaJLuaArchitecture owner){super(owner);}
    @FunctionalInterface private interface Query { Varargs call(Component component)throws Exception; }
    private Varargs withComponent(String address,Query query)throws Exception{
        Node node=machine.node(),target=node.network()==null?null:node.network().node(address);
        if(target instanceof Component component&&(target==node||component.canBeSeenFrom(node)))return query.call(component);
        return LuaValue.varargsOf(LuaValue.NIL,LuaValue.valueOf("no such component"));
    }
    void initialize(){
        LuaTable component=LuaValue.tableOf();
        component.set("list",LuaValueBridge.function(args->{var components=machine.components();synchronized(components){String filter=args.isstring(1)?args.tojstring(1):null;boolean exact=args.optboolean(2,false);LuaTable table=LuaValue.tableOf();components.forEach((address,name)->{if(filter==null||(exact?name.equals(filter):name.contains(filter)))table.set(address,name);});return table;}}));
        component.set("type",LuaValueBridge.function(args->{var components=machine.components();synchronized(components){String name=components.get(args.checkjstring(1));return name==null?LuaValue.varargsOf(LuaValue.NIL,LuaValue.valueOf("no such component")):LuaValue.valueOf(name);}}));
        component.set("slot",LuaValueBridge.function(args->{String address=args.checkjstring(1);return machine.components().containsKey(address)?LuaValue.valueOf(machine.host().componentSlot(address)):LuaValue.varargsOf(LuaValue.NIL,LuaValue.valueOf("no such component"));}));
        component.set("methods",LuaValueBridge.function(args->withComponent(args.checkjstring(1),target->{LuaTable table=LuaValue.tableOf();machine.methods(target.host()).forEach((name,annotation)->{LuaTable info=LuaValue.tableOf();info.set("direct",LuaValue.valueOf(annotation.direct()));info.set("getter",LuaValue.valueOf(annotation.getter()));info.set("setter",LuaValue.valueOf(annotation.setter()));table.set(name,info);});return table;})));
        component.set("invoke",LuaValueBridge.function(args->{String address=args.checkjstring(1),method=args.checkjstring(2);Object[] values=LuaValueBridge.toJava(args,3);return owner.invoke(()->machine.invoke(address,method,values));}));
        component.set("doc",LuaValueBridge.function(args->withComponent(args.checkjstring(1),target->{String method=args.checkjstring(2);return owner.documentation(()->{Callback annotation=machine.methods(target.host()).get(method);return annotation==null?null:annotation.doc();});})));
        lua().set("component",component);
    }
}
