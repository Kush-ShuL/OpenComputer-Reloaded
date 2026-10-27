package li.cil.oc.server.fs;

import li.cil.oc.api.fs.Handle;
import li.cil.oc.api.fs.Mode;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Shared descriptors and NBT restoration for the original stream-based file systems. */
public abstract class AbstractFileSystem implements li.cil.oc.api.fs.FileSystem {
    protected final Map<Integer, OpenHandle> handles = new LinkedHashMap<>();
    protected abstract OpenHandle createHandle(int id, String path, Mode mode) throws IOException;
    protected String clean(String path) { return FileSystem.validatePath(path); }
    @Override public synchronized int open(String path, Mode mode) throws FileNotFoundException {
        String normalized = clean(path);
        int id; do { id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE); } while (handles.containsKey(id));
        if (mode != Mode.Read && isReadOnly()) throw new FileNotFoundException("file system is read only");
        try { OpenHandle handle = createHandle(id, normalized, mode); handles.put(id, handle); return id; }
        catch (IOException error) { throw FileSystem.failure(error); }
    }
    @Override public synchronized Handle getHandle(int handle) { return handles.get(handle); }
    protected synchronized void closeHandles() { for (OpenHandle handle : new ArrayList<>(handles.values())) handle.close(); handles.clear(); }
    @Override public void close() { closeHandles(); }
    @Override public void load(CompoundTag nbt) { restoreHandles(nbt); }
    protected synchronized void restoreHandles(CompoundTag nbt) { closeHandles(); restore(nbt.getList("input", Tag.TAG_COMPOUND), Mode.Read); restore(nbt.getList("output", Tag.TAG_COMPOUND), Mode.Append); }
    private void restore(ListTag list, Mode mode) {
        for (Tag raw : list) {
            CompoundTag tag = (CompoundTag) raw;
            try { int id = tag.getInt("handle"); OpenHandle handle = createHandle(id, clean(tag.getString("path")), mode); handle.seek(tag.getLong("position")); handles.put(id, handle); }
            catch (IOException ignored) { /* The file may have disappeared since the world was saved. */ }
        }
    }
    @Override public synchronized void save(CompoundTag nbt) { saveHandles(nbt); }
    protected synchronized void saveHandles(CompoundTag nbt) {
        ListTag input = new ListTag(), output = new ListTag();
        for (OpenHandle handle : handles.values()) {
            CompoundTag tag = new CompoundTag(); tag.putInt("handle", handle.id); tag.putString("path", handle.path); tag.putLong("position", handle.position());
            (handle.mode == Mode.Read ? input : output).add(tag);
        }
        nbt.put("input", input); nbt.put("output", output); nbt.putLong("capacity.used", spaceUsed());
    }
    protected abstract class OpenHandle implements Handle {
        final int id; String path; final Mode mode; boolean closed;
        OpenHandle(int id, String path, Mode mode) { this.id=id; this.path=path; this.mode=mode; }
        protected void checkOpen() throws IOException { if(closed)throw new IOException("file is closed"); }
        protected void readable() throws IOException { checkOpen();if(mode!=Mode.Read)throw new IOException("bad file descriptor"); }
        protected void writable() throws IOException { checkOpen();if(mode==Mode.Read)throw new IOException("bad file descriptor"); }
        public void close() { synchronized(AbstractFileSystem.this){if(!closed){closed=true;handles.remove(id);release();}} }
        protected void release() {}
    }
}
