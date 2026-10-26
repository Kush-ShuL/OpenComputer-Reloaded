package li.cil.oc.server.machine.luaj;

import li.cil.oc.util.FontUtils;
import li.cil.repack.org.luaj.vm2.*;

final class UnicodeAPI extends LuaJAPI {
    UnicodeAPI(LuaJLuaArchitecture owner){super(owner);}
    void initialize(){
        LuaTable unicode=LuaValue.tableOf();
        unicode.set("lower",LuaValueBridge.function(args->LuaValue.valueOf(args.checkjstring(1).toLowerCase())));
        unicode.set("upper",LuaValueBridge.function(args->LuaValue.valueOf(args.checkjstring(1).toUpperCase())));
        unicode.set("char",LuaValueBridge.function(args->{StringBuilder text=new StringBuilder();for(int index=1;index<=args.narg();index++)text.appendCodePoint(args.checkint(index));return LuaValue.valueOf(text.toString());}));
        unicode.set("len",LuaValueBridge.function(args->{String text=args.checkjstring(1);return LuaValue.valueOf(text.codePointCount(0,text.length()));}));
        unicode.set("reverse",LuaValueBridge.function(args->{int[] points=args.checkjstring(1).codePoints().toArray();StringBuilder text=new StringBuilder();for(int index=points.length-1;index>=0;index--)text.appendCodePoint(points[index]);return LuaValue.valueOf(text.toString());}));
        unicode.set("sub",LuaValueBridge.function(args->{String text=args.checkjstring(1);int length=text.codePointCount(0,text.length()),first=args.checkint(2),last=args.narg()>2?args.checkint(3):length;int from=first<0?Math.max(0,length+first):Math.min(length,Math.max(0,first-1));int to=last<0?Math.max(0,length+last+1):Math.min(length,last);return LuaValue.valueOf(to<=from?"":text.substring(text.offsetByCodePoints(0,from),text.offsetByCodePoints(0,to)));}));
        unicode.set("isWide",LuaValueBridge.function(args->LuaValue.valueOf(FontUtils.wcwidth(args.checkjstring(1).codePointAt(0))>1)));
        unicode.set("charWidth",LuaValueBridge.function(args->LuaValue.valueOf(FontUtils.wcwidth(args.checkjstring(1).codePointAt(0)))));
        unicode.set("wlen",LuaValueBridge.function(args->LuaValue.valueOf(args.checkjstring(1).codePoints().map(point->Math.max(1,FontUtils.wcwidth(point))).sum())));
        unicode.set("wtrunc",LuaValueBridge.function(args->{String text=args.checkjstring(1);int count=args.checkint(2),width=0,end=0;while(end<text.length()){int point=text.codePointAt(end),next=Math.max(1,FontUtils.wcwidth(point));if(width+next>=count)break;width+=next;end+=Character.charCount(point);}return LuaValue.valueOf(text.substring(0,end));}));
        lua().set("unicode",unicode);
    }
}
