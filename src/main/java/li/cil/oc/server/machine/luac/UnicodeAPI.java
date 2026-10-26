package li.cil.oc.server.machine.luac;
import li.cil.oc.util.FontUtils;
final class UnicodeAPI extends NativeLuaAPI {
    UnicodeAPI(NativeLuaArchitecture owner){super(owner);}
    void initialize(){
        lua().newTable();
        bind("char",lua->{StringBuilder text=new StringBuilder();for(int index=1;index<=lua.getTop();index++)text.appendCodePoint(Math.toIntExact(lua.checkInteger(index)));lua.pushString(text.toString());return 1;});
        bind("len",lua->{String text=lua.checkString(1);lua.pushInteger(text.codePointCount(0,text.length()));return 1;});
        bind("lower",lua->{lua.pushString(lua.checkString(1).toLowerCase());return 1;});
        bind("upper",lua->{lua.pushString(lua.checkString(1).toUpperCase());return 1;});
        bind("reverse",lua->{int[] points=lua.checkString(1).codePoints().toArray();StringBuilder text=new StringBuilder();for(int index=points.length-1;index>=0;index--)text.appendCodePoint(points[index]);lua.pushString(text.toString());return 1;});
        bind("sub",lua->{String text=lua.checkString(1);int length=text.codePointCount(0,text.length()),first=Math.toIntExact(lua.checkInteger(2)),last=lua.getTop()>2?Math.toIntExact(lua.checkInteger(3)):length;int from=first<0?Math.max(0,length+first):Math.min(length,Math.max(0,first-1));int to=last<0?Math.max(0,length+last+1):Math.min(length,last);lua.pushString(to<=from?"":text.substring(text.offsetByCodePoints(0,from),text.offsetByCodePoints(0,to)));return 1;});
        bind("isWide",lua->{lua.pushBoolean(FontUtils.wcwidth(lua.checkString(1).codePointAt(0))>1);return 1;});
        bind("charWidth",lua->{lua.pushInteger(FontUtils.wcwidth(lua.checkString(1).codePointAt(0)));return 1;});
        bind("wlen",lua->{lua.pushInteger(lua.checkString(1).codePoints().map(point->Math.max(1,FontUtils.wcwidth(point))).sum());return 1;});
        bind("wtrunc",lua->{String text=lua.checkString(1);long count=lua.checkInteger(2);int width=0,end=0;while(end<text.length()){int point=text.codePointAt(end),next=Math.max(1,FontUtils.wcwidth(point));if(width+next>=count)break;width+=next;end+=Character.charCount(point);}lua.pushString(text.substring(0,end));return 1;});
        lua().setGlobal("unicode");
    }
}
