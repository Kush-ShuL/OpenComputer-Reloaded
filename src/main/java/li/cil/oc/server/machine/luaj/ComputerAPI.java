package li.cil.oc.server.machine.luaj;

import li.cil.oc.Settings;
import li.cil.oc.api.driver.item.*;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.network.Connector;
import li.cil.repack.org.luaj.vm2.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

final class ComputerAPI extends LuaJAPI {
    ComputerAPI(LuaJLuaArchitecture owner){super(owner);}
    void initialize(){
        LuaTable computer=LuaValue.tableOf();
        computer.set("realTime",LuaValueBridge.function(args->LuaValue.valueOf(System.currentTimeMillis()/1000d)));
        computer.set("uptime",LuaValueBridge.function(args->LuaValue.valueOf(machine.upTime())));
        computer.set("address",LuaValueBridge.function(args->machine.node().address()==null?LuaValue.NIL:LuaValue.valueOf(machine.node().address())));
        computer.set("freeMemory",LuaValueBridge.function(args->LuaValue.valueOf(owner.memory/2)));
        computer.set("totalMemory",LuaValueBridge.function(args->LuaValue.valueOf(owner.memory)));
        computer.set("pushSignal",LuaValueBridge.function(args->LuaValue.valueOf(machine.signal(args.checkjstring(1),LuaValueBridge.toJava(args,2)))));
        computer.set("tmpAddress",LuaValueBridge.function(args->machine.tmpAddress()==null?LuaValue.NIL:LuaValue.valueOf(machine.tmpAddress())));
        computer.set("users",LuaValueBridge.function(args->LuaValue.varargsOf(Arrays.stream(machine.users()).map(LuaValue::valueOf).toArray(LuaValue[]::new))));
        computer.set("addUser",LuaValueBridge.function(args->{machine.addUser(args.checkjstring(1));return LuaValue.TRUE;}));
        computer.set("removeUser",LuaValueBridge.function(args->LuaValue.valueOf(machine.removeUser(args.checkjstring(1)))));
        computer.set("energy",LuaValueBridge.function(args->LuaValue.valueOf(Settings.get().ignorePower?Double.POSITIVE_INFINITY:((Connector)machine.node()).globalBuffer())));
        computer.set("maxEnergy",LuaValueBridge.function(args->LuaValue.valueOf(((Connector)machine.node()).globalBufferSize())));
        computer.set("getArchitectures",LuaValueBridge.function(args->{for(ItemStack stack:machine.host().internalComponents()){var driver=li.cil.oc.api.Driver.driverFor(stack);Collection<Class<? extends Architecture>> architectures;if(driver instanceof MutableProcessor processor)architectures=processor.allArchitectures();else if(driver instanceof Processor processor)architectures=List.of(processor.architecture(stack));else continue;return LuaValue.listOf(architectures.stream().map(li.cil.oc.api.Machine::getArchitectureName).map(LuaValue::valueOf).toArray(LuaValue[]::new));}return LuaValue.tableOf();}));
        computer.set("getArchitecture",LuaValueBridge.function(args->{for(ItemStack stack:machine.host().internalComponents())if(li.cil.oc.api.Driver.driverFor(stack) instanceof Processor processor)return LuaValue.valueOf(li.cil.oc.api.Machine.getArchitectureName(processor.architecture(stack)));return LuaValue.NONE;}));
        computer.set("setArchitecture",LuaValueBridge.function(args->{String name=args.checkjstring(1);for(ItemStack stack:machine.host().internalComponents())if(li.cil.oc.api.Driver.driverFor(stack) instanceof MutableProcessor processor){for(Class<? extends Architecture> architecture:processor.allArchitectures())if(li.cil.oc.api.Machine.getArchitectureName(architecture).equals(name)){if(processor.architecture(stack)==architecture)return LuaValue.FALSE;processor.setArchitecture(stack,architecture);return LuaValue.TRUE;}return LuaValue.varargsOf(LuaValue.NIL,LuaValue.valueOf("unknown architecture"));}return LuaValue.NONE;}));
        lua().set("computer",computer);
    }
}
