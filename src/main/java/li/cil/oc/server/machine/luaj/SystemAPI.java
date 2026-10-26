package li.cil.oc.server.machine.luaj;
import li.cil.oc.Settings;
import li.cil.repack.org.luaj.vm2.*;
final class SystemAPI extends LuaJAPI {
    SystemAPI(LuaJLuaArchitecture owner){super(owner);}
    void initialize(){LuaTable system=LuaValue.tableOf();system.set("allowBytecode",LuaValueBridge.function(args->LuaValue.valueOf(Settings.get().allowBytecode)));system.set("allowGC",LuaValueBridge.function(args->LuaValue.valueOf(Settings.get().allowGC)));system.set("timeout",LuaValueBridge.function(args->LuaValue.valueOf(Settings.get().timeout)));lua().set("system",system);}
}
