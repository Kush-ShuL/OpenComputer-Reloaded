package li.cil.oc.server.component;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.event.level.LevelEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Real NeoForge persistent ticking tickets, reclaimed by the upgrade identity after loading. */
public final class UpgradeChunkloader extends UpgradeBase {
    private static final Map<ServerLevel, Map<UUID, Set<ChunkPos>>> RESTORED = new WeakHashMap<>();
    private static final Set<UpgradeChunkloader> LIVE = Collections.newSetFromMap(new WeakHashMap<>());
    public static final TicketController TICKETS = new TicketController(
            ResourceLocation.fromNamespaceAndPath("opencomputers", "chunkloader"), UpgradeChunkloader::restoreTickets);

    private UUID identity = UUID.randomUUID();
    private String identityAddress;
    private final Set<ChunkPos> chunks = new HashSet<>();
    // A moving host's current world is not necessarily the world which owns its tickets.
    private ServerLevel ticketLevel;
    private boolean active;

    public UpgradeChunkloader(EnvironmentHost host) {
        super(host, "chunkloader", "World stabilizer", "Realizer9001-CL", true);
        // The original chunkloader is visible to the network, including when installed in an agent.
        ((Component) node()).setVisibility(Visibility.Network);
    }

    public static void registerTickets(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    private static void restoreTickets(ServerLevel level, TicketHelper helper) {
        Map<UUID, Set<ChunkPos>> owners = new HashMap<>();
        // This controller only creates persistent UUID-owned, fully ticking tickets.
        helper.getBlockTickets().keySet().forEach(helper::removeAllTickets);
        helper.getEntityTickets().forEach((id, tickets) -> {
            if (!allowed(level) || tickets.ticking().isEmpty()) {
                helper.removeAllTickets(id);
                return;
            }
            tickets.nonTicking().forEach((long chunk) -> helper.removeTicket(id, chunk, false));
            Set<ChunkPos> previous = new HashSet<>();
            tickets.ticking().forEach((long chunk) -> previous.add(new ChunkPos(chunk)));
            owners.put(id, previous);
        });
        if (owners.isEmpty()) RESTORED.remove(level);
        else RESTORED.put(level, owners);
    }

    public static void saveWorld(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // As in ChunkloaderUpgradeHandler, tickets not claimed before the next world save are orphaned.
        // Only this dimension is considered: a save of the overworld must not discard an End owner.
        Map<UUID, Set<ChunkPos>> owners = RESTORED.remove(level);
        if (owners != null) owners.forEach((id, previous) -> {
            OpenComputers.LOGGER.warn("Removing orphaned chunkloader ticket {} in {}", id, level.dimension().location());
            previous.forEach(chunk -> TICKETS.forceChunk(level, id, chunk.x, chunk.z, false, true));
        });
    }

    public static void unloadWorld(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        RESTORED.remove(level);
        for (UpgradeChunkloader loader : new ArrayList<>(LIVE)) {
            if (loader.ticketLevel == level) loader.release();
        }
    }

    private static boolean allowed(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        var settings = Settings.get();
        List<Object> white = settings.chunkloadDimensionWhitelist;
        List<Object> black = settings.chunkloadDimensionBlacklist;
        return (white.isEmpty() || white.stream().anyMatch(value -> dimension.equals(dimension(value))))
                && black.stream().noneMatch(value -> dimension.equals(dimension(value)));
    }

    private static String dimension(Object value) {
        return switch (String.valueOf(value)) {
            case "0" -> "minecraft:overworld";
            case "-1" -> "minecraft:the_nether";
            case "1" -> "minecraft:the_end";
            default -> String.valueOf(value);
        };
    }

    private void release() {
        ServerLevel previousLevel = ticketLevel;
        Set<ChunkPos> previous = Set.copyOf(chunks);
        boolean wasActive = active;
        chunks.clear();
        ticketLevel = null;
        active = false;
        LIVE.remove(this);
        if (previousLevel != null) {
            for (ChunkPos chunk : previous) TICKETS.forceChunk(previousLevel, identity, chunk.x, chunk.z, false, true);
        }
        if (wasActive) changed();
    }

    public void updateLoadedChunks() {
        onServerThread(this::updateLoadedChunksOnServer);
    }

    private void updateLoadedChunksOnServer() {
        if (!active || !(host.world() instanceof ServerLevel level)) return;
        if (!allowed(level)) {
            release();
            return;
        }
        if (ticketLevel != level) {
            // Never remove an old dimension's ticket from the new dimension at the same coordinates.
            release();
            active = true;
            ticketLevel = level;
            LIVE.add(this);
        }
        ChunkPos center = new ChunkPos(position());
        Set<ChunkPos> wanted = new HashSet<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) wanted.add(new ChunkPos(center.x + x, center.z + z));
        }
        // The original releases the old edge before forcing the new edge when a robot/drone moves.
        for (ChunkPos chunk : Set.copyOf(chunks)) {
            if (!wanted.contains(chunk)) {
                TICKETS.forceChunk(level, identity, chunk.x, chunk.z, false, true);
                chunks.remove(chunk);
            }
        }
        for (ChunkPos chunk : wanted) {
            if (!chunks.contains(chunk)) {
                TICKETS.forceChunk(level, identity, chunk.x, chunk.z, true, true);
                chunks.add(chunk);
            }
        }
    }

    private boolean setActive(boolean enabled, boolean fail) {
        if (enabled && !active) {
            if (!(host.world() instanceof ServerLevel level)) return false;
            if (!allowed(level)) {
                if (fail) throw new IllegalArgumentException("this dimension is blacklisted");
                return false;
            }
            active = true;
            ticketLevel = level;
            LIVE.add(this);
            updateLoadedChunksOnServer();
            changed();
            return true;
        }
        if (!enabled && active) {
            release();
            return true;
        }
        return false;
    }

    @Callback(doc = "function():boolean -- Gets whether the chunkloader is currently active.")
    public Object[] isActive(Context context, Arguments args) {
        return new Object[]{active};
    }

    @Callback(doc = "function(enabled:boolean):boolean -- Enables or disables the chunkloader, returns true if active changed")
    public Object[] setActive(Context context, Arguments args) {
        return new Object[]{setActive(args.checkBoolean(0), true)};
    }

    @Override public boolean canUpdate() { return true; }

    @Override public void update() {
        if (!active || !(host.world() instanceof ServerLevel level)) return;
        if (level.getGameTime() % Settings.get().tickFrequency == 0) {
            if (!connector().tryChangeBuffer(-Settings.get().chunkloaderCost * Settings.get().tickFrequency)) release();
            else updateLoadedChunksOnServer();
        }
    }

    @Override public void onConnect(Node node) {
        if (node != node()) return;
        onServerThread(() -> {
            if (node().network() == null || !(host.world() instanceof ServerLevel level)) return;
            String address = node().address();
            if (identityAddress != null && !Objects.equals(identityAddress, address)) {
                // A cloned item receives a different network address. It must not share the original's tickets.
                release();
                identity = UUID.randomUUID();
            }
            identityAddress = address;
            Map<UUID, Set<ChunkPos>> restored = RESTORED.get(level);
            Set<ChunkPos> previous = restored == null ? null : restored.remove(identity);
            if (restored != null && restored.isEmpty()) RESTORED.remove(level);
            if (previous != null) {
                ticketLevel = level;
                chunks.addAll(previous);
                active = true;
                LIVE.add(this);
                if (!allowed(level)) release();
                else updateLoadedChunksOnServer();
                return;
            }
            // Modern built-in hosts expose their Context via MachineHost rather than implementing Context themselves.
            boolean running = host instanceof Context context && context.isRunning();
            if (!running && host instanceof MachineHost machineHost) {
                var machine = machineHost.machine();
                running = machine != null && machine.isRunning();
            }
            if (running) setActive(true, false);
        });
    }

    @Override public void onDisconnect(Node node) {
        if (node == node()) onServerThread(this::release);
    }

    @Override public void onMessage(Message message) {
        if (message.name().equals("computer.stopped")) onServerThread(() -> setActive(false, false));
        else if (message.name().equals("computer.started")) onServerThread(() -> setActive(true, false));
    }

    private void onServerThread(Runnable action) {
        ServerLevel level = host.world() instanceof ServerLevel current ? current : ticketLevel;
        if (level == null) return;
        var server = level.getServer();
        if (server.isSameThread()) action.run();
        else server.execute(action);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("ticketIdentity")) identity = tag.getUUID("ticketIdentity");
        identityAddress = tag.contains("ticketAddress") ? tag.getString("ticketAddress") : node().address();
    }

    @Override public void save(CompoundTag tag) {
        super.save(tag);
        tag.putUUID("ticketIdentity", identity);
        if (identityAddress != null) tag.putString("ticketAddress", identityAddress);
    }
}
