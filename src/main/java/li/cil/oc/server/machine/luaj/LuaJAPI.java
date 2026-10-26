package li.cil.oc.server.machine.luaj;
import li.cil.oc.api.machine.Machine;
import li.cil.repack.org.luaj.vm2.Globals;
abstract class LuaJAPI {
    protected final LuaJLuaArchitecture owner;
    protected final Machine machine;
    LuaJAPI(LuaJLuaArchitecture owner){this.owner=owner;machine=owner.machine;}
    protected Globals lua(){return owner.lua;}
    abstract void initialize();
}
