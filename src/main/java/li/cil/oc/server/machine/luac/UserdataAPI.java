package li.cil.oc.server.machine.luac;
import li.cil.oc.OpenComputers;
import li.cil.oc.api.Persistable;
import li.cil.oc.api.machine.Value;
import li.cil.oc.server.driver.Registry;
import li.cil.oc.server.machine.ArgumentsImpl;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;
final class UserdataAPI extends NativeLuaAPI {
    UserdataAPI(NativeLuaArchitecture owner){super(owner);}
    void initialize(){
        lua().newTable();
        bind("save",lua->{Persistable value=(Persistable)lua.toJavaObjectRaw(1);CompoundTag nbt=new CompoundTag();value.save(nbt,machine.host().world().registryAccess());ByteArrayOutputStream bytes=new ByteArrayOutputStream();NbtIo.write(nbt,new DataOutputStream(bytes));lua.pushString(value.getClass().getName());lua.pushByteArray(bytes.toByteArray());return 2;});
        bind("load",lua->{String name=lua.toString(1);Class<?> type=Class.forName(name);Persistable value=(Persistable)type.getDeclaredConstructor().newInstance();byte[] bytes=lua.toByteArray(2);CompoundTag nbt=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)),NbtAccounter.unlimitedHeap());value.load(nbt,machine.host().world().registryAccess());lua.pushJavaObjectRaw(value);return 1;});
        bind("apply",lua->{Value value=(Value)lua.toJavaObjectRaw(1);Object[] arguments=NativeValueBridge.toJavaArgs(lua,2);return owner.invoke(()->Registry.convert(new Object[]{value.apply(machine,new ArgumentsImpl(arguments))},machine.host().world()==null?null:machine.host().world().registryAccess()));});
        bind("unapply",lua->{Value value=(Value)lua.toJavaObjectRaw(1);Object[] arguments=NativeValueBridge.toJavaArgs(lua,2);return owner.invoke(()->{value.unapply(machine,new ArgumentsImpl(arguments));return null;});});
        bind("call",lua->{Value value=(Value)lua.toJavaObjectRaw(1);Object[] arguments=NativeValueBridge.toJavaArgs(lua,2);return owner.invoke(()->Registry.convert(value.call(machine,new ArgumentsImpl(arguments)),machine.host().world()==null?null:machine.host().world().registryAccess()));});
        bind("dispose",lua->{Value value=(Value)lua.toJavaObjectRaw(1);try{value.dispose(machine);}catch(Throwable error){OpenComputers.LOGGER.warn("Error disposing userdata {}",value.getClass().getName(),error);}return 0;});
        bind("methods",lua->{Object value=lua.toJavaObjectRaw(1);Map<String,Boolean> methods=new LinkedHashMap<>();machine.methods(value).forEach((name,annotation)->methods.put(name,annotation.direct()));NativeValueBridge.push(lua,methods);return 1;});
        bind("invoke",lua->{Value value=(Value)lua.toJavaObjectRaw(1);String method=lua.checkString(2);Object[] arguments=NativeValueBridge.toJavaArgs(lua,3);return owner.invoke(()->machine.invoke(value,method,arguments));});
        bind("doc",lua->{Value value=(Value)lua.toJavaObjectRaw(1);String method=lua.checkString(2);return owner.documentation(()->{var annotation=machine.methods(value).get(method);if(annotation==null)throw new NoSuchMethodException();return annotation.doc();});});
        lua().setGlobal("userdata");
    }
}
