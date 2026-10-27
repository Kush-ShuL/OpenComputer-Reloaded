package li.cil.oc.server.fs;
/** The RAM disk clears its contents when detached, as in the original Volatile mixin. */
public final class VolatileFileSystem extends VirtualFileSystem {
    public VolatileFileSystem(long capacity){super(capacity);}
    @Override public synchronized void close(){super.close();entries.clear();entries.put("",new Entry(true));recountUsage();}
}
