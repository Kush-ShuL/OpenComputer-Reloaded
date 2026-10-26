package li.cil.oc.server.machine.luac;
import li.cil.oc.Settings;
import java.util.StringJoiner;
final class SystemAPI extends NativeLuaAPI {
    SystemAPI(NativeLuaArchitecture owner){super(owner);}
    void initialize(){
        lua().pushJavaFunction(lua->{StringJoiner text=new StringJoiner("  ");for(int index=1;index<=lua.getTop();index++)text.add(switch(lua.type(index)){case NIL->"nil";case BOOLEAN->Boolean.toString(lua.toBoolean(index));case NUMBER->lua.isInteger(index)?Long.toString(lua.toInteger(index)):Double.toString(lua.toNumber(index));case STRING->lua.toString(index);case TABLE->"table";case FUNCTION->"function";case THREAD->"thread";default->"userdata";});System.out.println(text);return 0;});lua().setGlobal("print");
        lua().newTable();bind("allowBytecode",lua->{lua.pushBoolean(Settings.get().allowBytecode);return 1;});bind("allowGC",lua->{lua.pushBoolean(Settings.get().allowGC);return 1;});bind("timeout",lua->{lua.pushNumber(Settings.get().timeout);return 1;});lua().setGlobal("system");
    }
}
