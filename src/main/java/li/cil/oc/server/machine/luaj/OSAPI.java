package li.cil.oc.server.machine.luaj;

import li.cil.oc.util.GameTimeFormatter;
import li.cil.repack.org.luaj.vm2.*;

final class OSAPI extends LuaJAPI {
    OSAPI(LuaJLuaArchitecture owner){super(owner);}
    private double worldTime(){return(machine.worldTime()+6000)*60*60/1000;}
    private static int field(LuaTable table,String key,int fallback){LuaValue value=table.get(key);if(value.isint())return value.toint();if(fallback<0)throw new LuaError("field '"+key+"' missing in date table");return fallback;}
    void initialize(){
        LuaTable os=LuaValue.tableOf();
        os.set("clock",LuaValueBridge.function(args->LuaValue.valueOf(machine.cpuTime())));
        os.set("date",LuaValueBridge.function(args->{String format=args.narg()>0&&args.isstring(1)?args.tojstring(1):"%d/%m/%y %H:%M:%S";double time=args.narg()>1&&args.isnumber(2)?args.todouble(2):worldTime();var date=GameTimeFormatter.parse(time);if(format.startsWith("!"))format=format.substring(1);if(format.equals("*t")){LuaTable table=LuaValue.tableOf();table.set("year",date.year());table.set("month",date.month());table.set("day",date.day());table.set("hour",date.hour());table.set("min",date.minute());table.set("sec",date.second());table.set("wday",date.weekDay());table.set("yday",date.yearDay());return table;}return LuaValue.valueOf(GameTimeFormatter.format(format,date));}));
        os.set("time",LuaValueBridge.function(args->{if(args.isnoneornil(1))return LuaValue.valueOf(worldTime());LuaTable table=args.checktable(1);var time=GameTimeFormatter.mktime(field(table,"year",-1),field(table,"month",-1),field(table,"day",-1),field(table,"hour",12),field(table,"min",0),field(table,"sec",0));return time.isPresent()?LuaValue.valueOf(time.getAsInt()):LuaValue.NIL;}));
        lua().set("os",os);
    }
}
