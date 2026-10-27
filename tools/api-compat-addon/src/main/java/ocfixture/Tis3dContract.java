package ocfixture;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import li.cil.oc.api.API;
import li.cil.oc.api.Items;
import li.cil.oc.api.internal.Adapter;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Environment;
import li.cil.tis3d.api.serial.SerialInterface;
import li.cil.tis3d.api.serial.SerialInterfaceProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Registered TIS-3D provider, real OC adapter/case, and public serial handshake. */
public final class Tis3dContract {
    private static Run active;
    private Tis3dContract() {}
    public static void start(ServerLevel level, BlockPos pos, List<String> results) throws Exception {
        active = new Run(level, pos, results);
        active.start();
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        if (active == null || ++active.ticks < 3) return;
        try { active.verify(); }
        catch (Throwable exception) { active.results.add("FAIL I17 serial: " + exception); }
        finally { active.cleanup(); active = null; }
    }
    public static void close() { if (active != null) { active.cleanup(); active = null; } }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Run {
        final ServerLevel level;
        final BlockPos pos;
        final List<String> results;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final java.util.List<Long> forced = new java.util.ArrayList<>();
        Adapter adapter;
        Case machine;
        SerialInterfaceProvider provider;
        SerialInterface serial;
        int ticks;
        Run(ServerLevel level, BlockPos pos, List<String> results) { this.level = level; this.pos = pos; this.results = results; }
        void place(BlockPos p, BlockState state) {
            if (level.setChunkForced(p.getX() >> 4, p.getZ() >> 4, true)) forced.add(net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
            level.getChunkAt(p);
            require(level.isEmptyBlock(p), "TIS fixture cell occupied: " + p);
            restored.put(p, level.getBlockState(p));
            require(level.setBlock(p, state, 3), "Could not place actual TIS fixture host");
        }
        void start() {
            var registry = level.registryAccess().registryOrThrow(SerialInterfaceProvider.REGISTRY);
            provider = registry.get(ResourceLocation.parse("opencomputers:adapter"));
            boolean enabled = API.config.getStringList("integration.modBlacklist").stream().noneMatch(name -> name.trim().equalsIgnoreCase("tis3d"));
            require((provider != null) == enabled, "TIS provider registration ignored blacklist");
            if (!enabled) return;
            place(pos, Items.get("adapter").block().defaultBlockState());
            place(pos.west(), Items.get("case1").block().defaultBlockState());
            adapter = (Adapter) level.getBlockEntity(pos);
            machine = (Case) level.getBlockEntity(pos.west());
        }
        Object[] invoke(SerialInterface target, String method, Object... args) throws Exception {
            return ((Component) ((Environment) target).node()).invoke(method, machine.machine(), args);
        }
        void verify() throws Exception {
            if (provider == null) { results.add("PASS I17 tis3d blacklist omits real registry provider"); return; }
            for (Direction side : Direction.values()) require(provider.matches(level, pos, side), "Registered provider missed adapter side");
            require(!provider.matches(level, pos.above(), Direction.DOWN), "Provider matched empty block");
            serial = provider.getInterface(level, pos, Direction.EAST).orElseThrow();
            require(provider.stillValid(level, pos, Direction.EAST, serial), "New serial interface is invalid");
            require(provider.getDocumentationReference().isPresent(), "Serial documentation reference absent");
            require(!serial.canWrite(), "Incoming serial reads must start disabled");
            require(!serial.canRead(), "Fresh outgoing serial queue must be empty");
            require(((Environment) serial).node().network() == adapter.node().network(), "Serial component did not join actual adapter graph");
            invoke(serial, "setReading", true);
            for (int i = 0; i < 128; i++) { require(serial.canWrite(), "Incoming queue filled early"); serial.write((short) (i - 64)); }
            require(!serial.canWrite(), "Incoming serial queue exceeded its offered capacity");
            invoke(serial, "setReading", false);
            for (int i = 0; i < 128; i++) require(((Number) invoke(serial, "read")[0]).intValue() == i - 64, "Incoming FIFO changed signed values");
            require(invoke(serial, "read") == null && !serial.canWrite(), "Empty read or disabled read state changed");
            invoke(serial, "setReading", true);
            for (int i = 0; i < 128; i++) require(Boolean.TRUE.equals(invoke(serial, "write", i == 0 ? 65535 : i)[0]), "Outgoing queue filled early");
            Object[] full = invoke(serial, "write", 9);
            require(Boolean.FALSE.equals(full[0]) && full[1].toString().equals("buffer full"), "Full outgoing queue response changed");
            for (int i = 0; i < 128; i++) {
                short expected = (short) (i == 0 ? -1 : i);
                require(serial.canRead() && serial.peek() == expected && serial.peek() == expected, "Peek consumed or changed outgoing value");
                serial.skip();
            }
            require(!serial.canRead(), "Drained serial output remains readable");
            invoke(serial, "write", -32769);
            serial.write((short) -123);
            CompoundTag saved = new CompoundTag();
            serial.save(saved);
            String address = ((Environment) serial).node().address();
            serial.reset();
            require(!serial.canRead() && serial.canWrite(), "Reset did not clear queues while retaining reading state");
            ((Environment) serial).node().remove();
            SerialInterface restored = provider.getInterface(level, pos, Direction.EAST).orElseThrow();
            restored.load(saved);
            require(restored.canRead() && restored.peek() == 32767, "Persistent outgoing queue or 16-bit truncation changed");
            require(address.equals(((Environment) restored).node().address()), "Serial node identity changed on restoration");
            require(((Number) invoke(restored, "read")[0]).intValue() == -123 && restored.canWrite(), "Persistent incoming queue or read flag changed");
            ((Environment) restored).node().disconnect(adapter.node());
            require(restored.canRead() && ((Environment) restored).node().network() == adapter.node().network(), "Serial graph did not reconnect");
            restored.reset();
            ((Environment) restored).node().remove();
            level.removeBlock(pos, false);
            require(!provider.stillValid(level, pos, Direction.EAST, serial), "Removed adapter retained valid serial interface");
            results.add("PASS I17 actual registered provider, real adapter/case callbacks, signed-16 FIFO, 128-entry pressure, read cancellation, peek/skip, reset, persistence and graph reconnection");
        }
        void cleanup() {
            if (serial != null) serial.reset();
            for (var entry : restored.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
            for (long chunk : forced) level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk), net.minecraft.world.level.ChunkPos.getZ(chunk), false);
        }
    }
}
