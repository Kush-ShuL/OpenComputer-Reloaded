package li.cil.oc.server.machine.luac;
import li.cil.oc.api.machine.Machine;
import li.cil.repack.com.naef.jnlua.LuaState;
import net.minecraft.nbt.CompoundTag;
abstract class NativeLuaAPI {
    protected final NativeLuaArchitecture owner;
    protected final Machine machine;
    NativeLuaAPI(NativeLuaArchitecture owner){this.owner=owner;machine=owner.machine;}
    protected LuaState lua(){return owner.lua;}
    protected void bind(String name,NativeValueBridge.Function function){lua().pushJavaFunction(NativeValueBridge.function(function));lua().setField(-2,name);}
    abstract void initialize();
    void load(CompoundTag nbt){}
    void save(CompoundTag nbt){}
}
