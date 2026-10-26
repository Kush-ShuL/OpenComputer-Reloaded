package li.cil.oc.util;

import li.cil.oc.Settings;

/** Original worker priority: automatic means below normal; explicit values are clamped by Java. */
public final class ThreadPoolFactory {
    private ThreadPoolFactory(){}
    public static Thread daemon(Runnable task,String name){int configured=Settings.get().threadPriority;int priority=configured<Thread.MIN_PRIORITY?Thread.MIN_PRIORITY+(Thread.NORM_PRIORITY-Thread.MIN_PRIORITY)/2:Math.clamp(configured,Thread.MIN_PRIORITY,Thread.MAX_PRIORITY);Thread thread=new Thread(task,name);thread.setDaemon(true);thread.setPriority(priority);return thread;}
}
