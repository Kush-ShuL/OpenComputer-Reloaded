package li.cil.oc.server.machine.luac;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.item.*;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.network.Connector;
import net.minecraft.world.item.ItemStack;
import java.util.*;
final class ComputerAPI extends NativeLuaAPI {
    ComputerAPI(NativeLuaArchitecture owner){super(owner);}
    void initialize(){
        lua().newTable();
        bind("realTime",lua->{lua.pushNumber(System.currentTimeMillis()/1000d);return 1;});
        bind("uptime",lua->{lua.pushNumber(machine.upTime());return 1;});
        bind("address",lua->{String address=machine.node().address();if(address==null)lua.pushNil();else lua.pushString(address);return 1;});
        bind("freeMemory",lua->{lua.pushInteger((int)(Math.min(lua.getFreeMemory(),lua.getTotalMemory()-owner.kernelMemory)/owner.ramScale));return 1;});
        bind("totalMemory",lua->{lua.pushInteger((int)((lua.getTotalMemory()-owner.kernelMemory)/owner.ramScale));return 1;});
        bind("pushSignal",lua->{lua.pushBoolean(machine.signal(lua.checkString(1),NativeValueBridge.toJavaArgs(lua,2)));return 1;});
        bind("tmpAddress",lua->{String address=machine.tmpAddress();if(address==null)lua.pushNil();else lua.pushString(address);return 1;});
        bind("users",lua->{String[] users=machine.users();for(String name:users)lua.pushString(name);return users.length;});
        bind("addUser",lua->{try{machine.addUser(lua.checkString(1));lua.pushBoolean(true);return 1;}catch(Throwable error){lua.pushNil();lua.pushString(error.getMessage()==null?error.toString():error.getMessage());return 2;}});
        bind("removeUser",lua->{lua.pushBoolean(machine.removeUser(lua.checkString(1)));return 1;});
        bind("energy",lua->{lua.pushNumber(Settings.get().ignorePower?Double.POSITIVE_INFINITY:((Connector)machine.node()).globalBuffer());return 1;});
        bind("maxEnergy",lua->{lua.pushNumber(((Connector)machine.node()).globalBufferSize());return 1;});
        bind("getArchitectures",lua->{for(ItemStack stack:machine.host().internalComponents()){var driver=li.cil.oc.api.Driver.driverFor(stack);Collection<Class<? extends Architecture>> architectures;if(driver instanceof MutableProcessor processor)architectures=processor.allArchitectures();else if(driver instanceof Processor processor)architectures=List.of(processor.architecture(stack));else continue;NativeValueBridge.push(lua,architectures.stream().map(li.cil.oc.api.Machine::getArchitectureName).toList());return 1;}lua.newTable();return 1;});
        bind("getArchitecture",lua->{for(ItemStack stack:machine.host().internalComponents())if(li.cil.oc.api.Driver.driverFor(stack) instanceof Processor processor){lua.pushString(li.cil.oc.api.Machine.getArchitectureName(processor.architecture(stack)));return 1;}return 0;});
        bind("setArchitecture",lua->{String name=lua.checkString(1);for(ItemStack stack:machine.host().internalComponents())if(li.cil.oc.api.Driver.driverFor(stack) instanceof MutableProcessor processor){for(Class<? extends Architecture> architecture:processor.allArchitectures())if(li.cil.oc.api.Machine.getArchitectureName(architecture).equals(name)){boolean changed=architecture!=processor.architecture(stack);if(changed)processor.setArchitecture(stack,architecture);lua.pushBoolean(changed);return 1;}lua.pushNil();lua.pushString("unknown architecture");return 2;}return 0;});
        lua().setGlobal("computer");
    }
}
