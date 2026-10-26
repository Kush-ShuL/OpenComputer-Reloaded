package li.cil.oc.util;

import java.util.*;

/** OC's UTC/English Lua os.date and os.time formatter. */
public final class GameTimeFormatter {
    private GameTimeFormatter() {}
    public record DateTime(int year,int month,int day,int weekDay,int yearDay,int hour,int minute,int second) {}
    private static final String[] WEEK={"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
    private static final String[] SHORT_WEEK={"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
    private static final String[] MONTH={"January","February","March","April","May","June","July","August","September","October","November","December"};
    private static final String[] SHORT_MONTH={"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
    public static DateTime parse(double time){
        Calendar c=new GregorianCalendar(TimeZone.getTimeZone("UTC"));c.setTimeInMillis((long)(time*1000));
        return new DateTime(c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.DAY_OF_WEEK),c.get(Calendar.DAY_OF_YEAR),c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),c.get(Calendar.SECOND));
    }
    private static String pad(int n,int width){return String.format(Locale.ROOT,"%0"+width+"d",n);}
    public static String format(String pattern,DateTime t){
        StringBuilder result=new StringBuilder();
        for(int i=0;i<pattern.length();i++){
            char c=pattern.charAt(i);if(c!='%'||i+1==pattern.length()){result.append(c);continue;}
            char code=pattern.charAt(++i);
            result.append(switch(code){
                case 'a'->SHORT_WEEK[t.weekDay-1];case 'A'->WEEK[t.weekDay-1];case 'b','h'->SHORT_MONTH[t.month-1];case 'B'->MONTH[t.month-1];
                case 'c'->format("%a %b %e %H:%M:%S %Y",t);case 'C'->pad(t.year/100,2);case 'd'->pad(t.day,2);case 'D','x'->format("%m/%d/%y",t);
                case 'e'->String.format(Locale.ROOT,"%2d",t.day);case 'F'->format("%Y-%m-%d",t);case 'H'->pad(t.hour,2);case 'I'->pad((t.hour+11)%12+1,2);
                case 'j'->pad(t.yearDay,3);case 'm'->pad(t.month,2);case 'M'->pad(t.minute,2);case 'n'->"\n";case 'p'->t.hour<12?"AM":"PM";
                case 'r'->format("%I:%M:%S %p",t);case 'R'->format("%H:%M",t);case 'S'->pad(t.second,2);case 't'->"\t";case 'T','X'->format("%H:%M:%S",t);
                case 'w'->Integer.toString(t.weekDay-1);case 'y'->pad(t.year%100,2);case 'Y'->pad(t.year,4);case '%'->"%";default->"";
            });
        }
        return result.toString();
    }
    public static OptionalInt mktime(int year,int month,int day,int hour,int minute,int second){
        Calendar c=new GregorianCalendar(TimeZone.getTimeZone("UTC"));c.clear();
        c.set(year,month-1,day,hour,minute,second);return OptionalInt.of((int)(c.getTimeInMillis()/1000));
    }
}
