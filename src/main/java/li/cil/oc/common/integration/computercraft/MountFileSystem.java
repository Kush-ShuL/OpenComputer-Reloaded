package li.cil.oc.common.integration.computercraft;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.MountConstants;
import dan200.computercraft.api.filesystem.WritableMount;
import li.cil.oc.api.fs.FileSystem;
import li.cil.oc.api.fs.Handle;
import li.cil.oc.api.fs.Mode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** OpenComputers filesystem backed directly by a public ComputerCraft mount. */
public final class MountFileSystem implements FileSystem {
    private final Mount mount;
    private final WritableMount writable;
    private final Map<Integer, ChannelHandle> handles = new LinkedHashMap<>();

    public MountFileSystem(Mount mount) {
        this.mount = Objects.requireNonNull(mount);
        writable = mount instanceof WritableMount value ? value : null;
    }

    private static String clean(String path) throws IOException {
        return li.cil.oc.server.fs.FileSystem.validatePath(path);
    }

    @Override
    public boolean isReadOnly() {
        return writable == null;
    }

    @Override
    public long spaceTotal() {
        return writable == null ? 0 : writable.getCapacity();
    }

    @Override
    public long spaceUsed() {
        if (writable == null) return 0;
        try {
            return Math.max(0, writable.getCapacity() - writable.getRemainingSpace());
        } catch (IOException error) {
            throw li.cil.oc.server.fs.FileSystem.failure(error);
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            return mount.exists(clean(path));
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public boolean isDirectory(String path) {
        try {
            return mount.isDirectory(clean(path));
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public long size(String path) {
        try {
            String normalized = clean(path);
            return mount.isDirectory(normalized) ? 0 : mount.getSize(normalized);
        } catch (IOException | RuntimeException ignored) {
            return 0;
        }
    }

    @Override
    public long lastModified(String path) {
        try {
            return mount.getAttributes(clean(path)).lastModifiedTime().toMillis();
        } catch (IOException | RuntimeException ignored) {
            return 0;
        }
    }

    @Override
    public String[] list(String path) {
        try {
            String normalized = clean(path);
            if (!mount.isDirectory(normalized)) return null;
            var entries = new ArrayList<String>();
            mount.list(normalized, entries);
            for (int i = 0; i < entries.size(); i++) {
                String name = entries.get(i);
                String child = normalized.isEmpty() ? name : normalized + "/" + name;
                if (mount.isDirectory(child) && !name.endsWith("/")) entries.set(i, name + "/");
            }
            return entries.toArray(String[]::new);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public synchronized boolean delete(String path) {
        if (writable == null) return false;
        try {
            String normalized = clean(path);
            if (normalized.isEmpty() || !mount.exists(normalized)) return false;
            if (mount.isDirectory(normalized)) {
                var children = new ArrayList<String>();
                mount.list(normalized, children);
                if (!children.isEmpty()) return false;
            }
            writable.delete(normalized);
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public synchronized boolean makeDirectory(String path) {
        if (writable == null) return false;
        try {
            String normalized = clean(path);
            if (normalized.isEmpty() || mount.exists(normalized) || !mount.isDirectory(parent(normalized))) return false;
            writable.makeDirectory(normalized);
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    @Override
    public synchronized boolean rename(String from, String to) throws FileNotFoundException {
        if (writable == null) return false;
        try {
            String source = clean(from), target = clean(to);
            if (!mount.exists(source)) throw new FileNotFoundException(from);
            if (source.isEmpty() || target.isEmpty()) return false;
            if (source.equals(target)) return true;
            if (mount.exists(target) || !mount.isDirectory(parent(target))) return false;
            writable.rename(source, target);
            for (ChannelHandle handle : handles.values()) {
                if (handle.path.equals(source) || handle.path.startsWith(source + "/")) {
                    handle.path = target + handle.path.substring(source.length());
                }
            }
            return true;
        } catch (FileNotFoundException error) {
            throw error;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public boolean setLastModified(String path, long time) {
        // The public mount API exposes attributes but has no timestamp setter.
        return false;
    }

    @Override
    public synchronized int open(String path, Mode mode) throws FileNotFoundException {
        Objects.requireNonNull(mode, "mode");
        try {
            String normalized = clean(path);
            if (mode != Mode.Read && writable == null) throw new IOException("file system is read only");
            for (ChannelHandle handle : handles.values()) {
                if (handle.path.equals(normalized) && handle.mode != Mode.Read && mode != Mode.Read) {
                    throw new IOException("file is already open for writing");
                }
            }
            int id;
            do {
                id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
            } while (handles.containsKey(id));
            ChannelHandle handle = createHandle(id, normalized, mode, false);
            handles.put(id, handle);
            return id;
        } catch (IOException | RuntimeException error) {
            var failure = new FileNotFoundException(error.getMessage());
            failure.initCause(error);
            throw failure;
        }
    }

    private ChannelHandle createHandle(int id, String path, Mode mode, boolean restoring) throws IOException {
        if (mount.isDirectory(path)) throw new FileNotFoundException(path);
        SeekableByteChannel channel;
        if (mode == Mode.Read) {
            channel = mount.openForRead(path);
        } else {
            if (writable == null) throw new IOException("file system is read only");
            // Reopening a saved write descriptor must not truncate its existing contents.
            channel = writable.openFile(path, mode == Mode.Append ? MountConstants.APPEND_OPTIONS :
                    restoring ? Set.of(StandardOpenOption.WRITE) : MountConstants.WRITE_OPTIONS);
        }
        if (channel == null) throw new IOException("mount did not return a file channel");
        try {
            if (mode == Mode.Append) channel.position(channel.size());
            return new ChannelHandle(id, path, mode, channel);
        } catch (IOException | RuntimeException error) {
            try {
                channel.close();
            } catch (IOException suppressed) {
                error.addSuppressed(suppressed);
            }
            throw error;
        }
    }

    @Override
    public synchronized Handle getHandle(int handle) {
        return handles.get(handle);
    }

    @Override
    public synchronized void close() {
        IOException failure = null;
        for (ChannelHandle handle : new ArrayList<>(handles.values())) {
            try {
                handle.closeChannel();
            } catch (IOException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw li.cil.oc.server.fs.FileSystem.failure(failure);
    }

    @Override
    public synchronized void load(CompoundTag nbt) {
        close();
        restore(nbt.getList("input", Tag.TAG_COMPOUND), Mode.Read);
        restore(nbt.getList("output", Tag.TAG_COMPOUND), Mode.Append);
    }

    private void restore(ListTag entries, Mode fallback) {
        for (Tag raw : entries) {
            CompoundTag tag = (CompoundTag) raw;
            ChannelHandle handle = null;
            try {
                int id = tag.getInt("handle");
                if (id <= 0 || handles.containsKey(id)) continue;
                Mode mode = tag.contains("mode") ? Mode.valueOf(tag.getString("mode")) : fallback;
                if ((mode == Mode.Read) != (fallback == Mode.Read)) continue;
                String path = clean(tag.getString("path"));
                if (!mount.exists(path)) continue;
                handle = createHandle(id, path, mode, true);
                handle.seek(tag.getLong("position"));
                handles.put(id, handle);
            } catch (IOException | RuntimeException ignored) {
                // Media may have changed or disappeared since this state was saved.
                if (handle != null) {
                    try {
                        handle.closeChannel();
                    } catch (IOException ignoredClose) {
                    }
                }
            }
        }
    }

    @Override
    public synchronized void save(CompoundTag nbt) {
        ListTag input = new ListTag(), output = new ListTag();
        for (ChannelHandle handle : handles.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("handle", handle.id);
            tag.putString("path", handle.path);
            tag.putString("mode", handle.mode.name());
            tag.putLong("position", handle.position());
            (handle.mode == Mode.Read ? input : output).add(tag);
        }
        nbt.put("input", input);
        nbt.put("output", output);
        nbt.putLong("capacity.used", spaceUsed());
    }

    private final class ChannelHandle implements Handle {
        private final int id;
        private String path;
        private final Mode mode;
        private final SeekableByteChannel channel;
        private boolean closed;

        private ChannelHandle(int id, String path, Mode mode, SeekableByteChannel channel) {
            this.id = id;
            this.path = path;
            this.mode = mode;
            this.channel = channel;
        }

        private void checkOpen() throws IOException {
            if (closed || !channel.isOpen()) throw new IOException("file is closed");
        }

        @Override
        public long position() {
            synchronized (MountFileSystem.this) {
                try {
                    checkOpen();
                    return channel.position();
                } catch (IOException error) {
                    throw li.cil.oc.server.fs.FileSystem.failure(error);
                }
            }
        }

        @Override
        public long length() {
            synchronized (MountFileSystem.this) {
                try {
                    checkOpen();
                    return channel.size();
                } catch (IOException error) {
                    throw li.cil.oc.server.fs.FileSystem.failure(error);
                }
            }
        }

        @Override
        public int read(byte[] into) throws IOException {
            synchronized (MountFileSystem.this) {
                checkOpen();
                if (mode != Mode.Read) throw new IOException("bad file descriptor");
                return channel.read(ByteBuffer.wrap(into));
            }
        }

        @Override
        public long seek(long to) throws IOException {
            synchronized (MountFileSystem.this) {
                checkOpen();
                if (to < 0) throw new IOException("invalid offset");
                channel.position(to);
                return channel.position();
            }
        }

        @Override
        public void write(byte[] value) throws IOException {
            synchronized (MountFileSystem.this) {
                checkOpen();
                if (mode == Mode.Read) throw new IOException("bad file descriptor");
                ByteBuffer bytes = ByteBuffer.wrap(value);
                while (bytes.hasRemaining()) {
                    if (channel.write(bytes) <= 0) throw new IOException("could not write to file");
                }
            }
        }

        private void closeChannel() throws IOException {
            if (closed) return;
            closed = true;
            handles.remove(id, this);
            channel.close();
        }

        @Override
        public void close() {
            synchronized (MountFileSystem.this) {
                try {
                    closeChannel();
                } catch (IOException error) {
                    throw li.cil.oc.server.fs.FileSystem.failure(error);
                }
            }
        }
    }
}
