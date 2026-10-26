package li.cil.oc.server.machine.luac;
import li.cil.oc.util.GameTimeFormatter;
import li.cil.repack.com.naef.jnlua.*;
final class OSAPI extends NativeLuaAPI {
    OSAPI(NativeLuaArchitecture owner){super(owner);}
    private static int field(LuaState lua,String key,int fallback){lua.getField(1,key);Long value=lua.toIntegerX(-1);lua.pop(1);if(value!=null)return value.intValue();if(fallback<0)throw new IllegalArgumentException("field '"+key+"' missing in date table");return fallback;}
    void initialize(){
        lua().getGlobal("os");
        bind("clock",lua->{lua.pushNumber(machine.cpuTime());return 1;});
        bind("date",lua->{String format=lua.getTop()>0&&lua.isString(1)?lua.toString(1):"%d/%m/%y %H:%M:%S";double time=lua.getTop()>1&&lua.isNumber(2)?lua.toNumber(2):(machine.worldTime()+6000)*60*60/1000d;var date=GameTimeFormatter.parse(time);if(format.startsWith("!"))format=format.substring(1);if(format.equals("*t")){lua.newTable(0,8);lua.pushInteger(date.year());lua.setField(-2,"year");lua.pushInteger(date.month());lua.setField(-2,"month");lua.pushInteger(date.day());lua.setField(-2,"day");lua.pushInteger(date.hour());lua.setField(-2,"hour");lua.pushInteger(date.minute());lua.setField(-2,"min");lua.pushInteger(date.second());lua.setField(-2,"sec");lua.pushInteger(date.weekDay());lua.setField(-2,"wday");lua.pushInteger(date.yearDay());lua.setField(-2,"yday");}else lua.pushString(GameTimeFormatter.format(format,date));return 1;});
        bind("time",lua->{if(lua.isNoneOrNil(1)){lua.pushNumber((machine.worldTime()+6000)*60*60/1000d);return 1;}lua.checkType(1,LuaType.TABLE);lua.setTop(1);var time=GameTimeFormatter.mktime(field(lua,"year",-1),field(lua,"month",-1),field(lua,"day",-1),field(lua,"hour",12),field(lua,"min",0),field(lua,"sec",0));if(time.isPresent())lua.pushNumber(time.getAsInt());else lua.pushNil();return 1;});
        lua().pop(1);
    }
}
