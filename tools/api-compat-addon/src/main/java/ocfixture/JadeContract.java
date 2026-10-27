package ocfixture;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import li.cil.oc.api.API;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real OC world devices queried through Jade's automatically registered server provider. */
public final class JadeContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-jade-contract");
    private static final List<String> RESULTS = new CopyOnWriteArrayList<>();
    private static Run active;

    private JadeContract() {}

    public static boolean enabled() { return Boolean.getBoolean("ocfixture.jadeOnly"); }
    public static boolean isRunning() { return active != null; }
    public static List<String> results() { return List.copyOf(RESULTS); }

    public static String run(ServerLevel level) throws Exception {
        require(active == null, "A Jade contract is already running");
        RESULTS.clear();
        require(API.config != null && API.network != null && API.driver != null, "OC SDK facades are unavailable");
        Set<String> blacklist = new LinkedHashSet<>();
        for (String entry : API.config.getStringList("integration.modBlacklist")) blacklist.add(entry.trim().toLowerCase(Locale.ROOT));
        String selected = System.getProperty("ocfixture.jadeBlacklist", System.getProperty("ocJadeBlacklist", ""));
        Set<String> requested = new LinkedHashSet<>();
        for (String entry : selected.split(",")) if (!entry.isBlank()) requested.add(entry.trim().toLowerCase(Locale.ROOT));
        require(requested.stream().allMatch(id -> id.equals("jade") || id.equals("waila")), "Select only jade or historical waila for this contract");
        boolean blocked = blacklist.contains("jade") || blacklist.contains("waila");
        require(requested.isEmpty() ? !blocked : blacklist.containsAll(requested), "Isolated Jade blacklist differs from ocJadeBlacklist selection");
        boolean present = ModList.get().isLoaded("jade");
        Object gate = invokeStatic("li.cil.oc.client.integration.jade.JadeIntegration", "enabled");
        require(Boolean.valueOf(present && !blocked).equals(gate), "Jade's real optional-mod gate differs from the selected runtime/configuration");
        if (!present) {
            pass("dedicated server starts without Jade; the OC gate returns false without loading Jade provider/accessor classes");
            limits();
            return "Completed absent-Jade dedicated contract";
        }
        Run run = new Run(level, blocked);
        active = run;
        try { run.start(); }
        catch (Exception | Error error) { run.fail(error); throw error; }
        return "Started real Jade server-provider contract, integration=" + !blocked;
    }

    public static void tick() {
        Run run = active;
        if (run == null) return;
        try { run.tick(); }
        catch (Throwable error) { run.fail(error); }
    }

    public static void close() {
        if (active != null) active.fail(new AssertionError("Server stopped before the Jade contract completed"));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void pass(String message) { RESULTS.add("PASS Jade: " + message); LOGGER.info("JADE_CONTRACT PASS {}", message); }
    private static void limits() {
        RESULTS.add("LIMIT Jade: client tooltip formatting, the three client toggles and multiplayer synchronization were not exercised on this dedicated server");
        RESULTS.add("LIMIT Jade: busy assembler progress/output-name serialization was not exercised; no pending-output fields or synthetic assembly template were injected");
    }
    private static ItemStack item(String name) {
        ItemStack stack = Objects.requireNonNull(li.cil.oc.api.Items.get(name), "Missing SDK item " + name).createItemStack(1);
        require(!stack.isEmpty(), "SDK returned an empty " + name);
        return stack;
    }
    private static Block block(String name) { return Objects.requireNonNull(Objects.requireNonNull(li.cil.oc.api.Items.get(name), "Missing SDK block " + name).block(), "SDK descriptor has no block: " + name); }
    private static Object invokeStatic(String className, String method) throws Exception {
        try { return Class.forName(className).getMethod(method).invoke(null); }
        catch (InvocationTargetException error) { throw new IllegalStateException("Public OC gate failed", error.getCause()); }
    }
    private static Object invoke(BlockEntity target, String method) throws Exception {
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (InvocationTargetException error) { throw new IllegalStateException("Public device accessor failed: " + method, error.getCause()); }
    }

    private static final class Run {
        final ServerLevel level;
        final boolean blocked;
        final BlockPos origin = new BlockPos(Integer.getInteger("ocfixture.jadeX", 4160), Integer.getInteger("ocfixture.jadeY", 96), Integer.getInteger("ocfixture.jadeZ", 4096));
        final BlockPos drivePosition = origin.east(4), chargerPosition = origin.east(8), relayPosition = origin.east(12);
        final BlockPos signalPosition = chargerPosition.north();
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final List<ItemEntity> drops = new ArrayList<>();
        final String floppyName = "OC Jade fixture " + UUID.randomUUID();
        BlockEntity computer, drive, charger, relay;
        FakePlayer actor;
        int ticks;
        boolean complete;

        Run(ServerLevel level, boolean blocked) { this.level = level; this.blocked = blocked; }

        void start() {
            List<BlockPos> positions = blocked ? List.of(origin) : List.of(origin, drivePosition, chargerPosition, relayPosition, signalPosition);
            for (BlockPos pos : positions) {
                require(pos.getY() > level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight(), "Jade fixture position lies outside build height");
                if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
                level.getChunkAt(pos);
                require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Jade fixture will not overwrite occupied cell " + pos);
            }
            computer = place(origin, block("case1").defaultBlockState());
            require(computer instanceof Case && computer instanceof SidedEnvironment, "Placed Case lacks its real public network interfaces");
            actor = new FakePlayer(level, new GameProfile(UUID.fromString("c64004ba-0467-4697-86e3-d1801e4d7f56"), "OC_Jade"));
            actor.setPos(origin.getX() + .5, origin.getY() + 1, origin.getZ() + .5);
            if (blocked) return;
            drive = place(drivePosition, block("diskdrive").defaultBlockState());
            charger = place(chargerPosition, block("charger").defaultBlockState());
            relay = place(relayPosition, block("relay").defaultBlockState());
            require(drive instanceof Container && drive instanceof Environment && relay instanceof Container && relay instanceof SidedEnvironment,
                    "Placed drive/relay lack their real inventory/network interfaces");
            Container inventory = (Container) relay;
            ItemStack card = item("wlancard1");
            require(inventory.canPlaceItem(3, card), "Real relay rejected its wireless card socket");
            inventory.setItem(3, card);
        }

        BlockEntity place(BlockPos pos, BlockState state) {
            require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Jade fixture will not overwrite occupied cell " + pos);
            restored.put(pos, level.getBlockState(pos));
            require(level.setBlock(pos, state, 3), "Could not place real fixture block at " + pos);
            return level.getBlockEntity(pos);
        }

        void tick() throws Exception {
            require(++ticks <= 40, "Jade world devices did not initialize within 40 server ticks");
            if (ticks == 4) {
                if (blocked) {
                    require(!JadePresent.registered(computer), "Blacklisted OC Jade provider was still automatically registered");
                    pass("real Jade registration contains no opencomputers:information provider when the selected jade/waila alias is blacklisted");
                    finish(); return;
                }
                require(JadePresent.registered(computer), "Jade did not automatically discover/register the actual OC server provider");
                pass("dedicated-side annotation discovery registered the actual opencomputers:information server provider");
                caseData();
                relayData();
                requireNoDiskAddress("empty drive");
                require(Double.compare(JadePresent.read(charger, Direction.UP, actor).getDouble("chargeSpeed"), 0) == 0, "Unpowered-redstone charger did not report its initial zero speed");
                ItemStack floppy = item("floppy");
                floppy.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(floppyName));
                Container inventory = (Container) drive;
                require(inventory.canPlaceItem(0, floppy), "Real disk drive rejected its floppy socket");
                inventory.setItem(0, floppy);
                place(signalPosition, Blocks.REDSTONE_BLOCK.defaultBlockState());
            } else if (ticks == 8) {
                Node filesystem = (Node) invoke(drive, "filesystemNode");
                require(filesystem != null && filesystem.address() != null && !filesystem.address().isEmpty(), "Inserted real floppy did not mount a filesystem node");
                CompoundTag data = JadePresent.read(drive, Direction.NORTH, actor);
                require(filesystem.address().equals(data.getString("address")), "Jade drive address differs from the actual mounted filesystem");
                require(!Objects.equals(((Environment) drive).node().address(), data.getString("address")), "Jade displayed the drive node in place of the floppy filesystem");
                pass("real Container insertion exposes the mounted floppy filesystem address instead of the drive address");
                CompoundTag charge = JadePresent.read(charger, Direction.UP, actor);
                double actual = charger.getClass().getField("chargeSpeed").getDouble(charger);
                require(actual == 1 && Double.compare(actual, charge.getDouble("chargeSpeed")) == 0, "Placed redstone block did not drive the actual charger/Jade speed to one");
                pass("real charger/redstone-neighbor updates produce chargeSpeed=0 then 1 in the server provider");
                try {
                    require(Boolean.TRUE.equals(drive.getClass().getMethod("ejectMedium", double.class).invoke(drive, 0d)), "Real disk-drive eject did not remove its floppy");
                } catch (InvocationTargetException error) { throw new IllegalStateException("Actual drive eject failed", error.getCause()); }
                drops.addAll(level.getEntitiesOfClass(ItemEntity.class, new AABB(drivePosition).inflate(2), entity -> floppyName.equals(entity.getItem().getHoverName().getString())));
                require(drops.size() == 1, "Actual drive eject did not create exactly the fixture's owned floppy item");
            } else if (ticks == 10) {
                require(((Container) drive).isEmpty() && invoke(drive, "filesystemNode") == null, "Actual eject left a mounted floppy");
                requireNoDiskAddress("ejected drive");
                pass("real drive eject clears its filesystem/address; fresh Jade data has no stale floppy address");
                finish();
            }
        }

        void caseData() {
            SidedEnvironment sided = (SidedEnvironment) computer;
            Node actual = ((Environment) computer).node();
            require(actual instanceof Connector && actual instanceof Component && actual.reachability() != Visibility.None, "Real Case did not expose an analyzable component/connector");
            Connector connector = (Connector) actual;
            connector.changeBuffer(Math.min(137.75, connector.localBufferSize()));
            require(connector.localBuffer() > 0 && connector.localBufferSize() > 0, "Actual Case connector could not hold fixture energy");
            int connected = 0, blockedFaces = 0;
            for (Direction side : Direction.values()) {
                ListTag nodes = JadePresent.read(computer, side, actor).getList("nodes", Tag.TAG_COMPOUND);
                require(nodes.size() == 6, "Case server data lost its six world-side slots");
                Node expected = sided.sidedNode(side);
                CompoundTag node = nodes.getCompound(side.get3DDataValue());
                if (expected == null || expected.reachability() == Visibility.None) {
                    require(node.isEmpty(), "A disconnected Case face exposed node data: " + side); blockedFaces++;
                } else {
                    require(expected.address() != null && !expected.address().isEmpty() && expected.address().equals(node.getString("address")), "Actual Case address mismatch on " + side);
                    require(node.contains("buffer", Tag.TAG_INT) && node.contains("bufferSize", Tag.TAG_INT)
                            && node.getInt("buffer") == (int) connector.localBuffer() && node.getInt("bufferSize") == (int) connector.localBufferSize(), "Actual local connector energy mismatch on " + side);
                    require(((Component) expected).name().equals(node.getString("componentName")), "Actual Case component name mismatch on " + side);
                    connected++;
                }
            }
            require(connected == 5 && blockedFaces == 1, "Case did not preserve its five connected faces and one closed front");
            pass("actual Case address/component name/local connector energy match all six world-side slots, including its closed front");
        }

        void relayData() throws Exception {
            String[] snapshot = (String[]) invoke(relay, "componentAddresses");
            CompoundTag data = JadePresent.read(relay, Direction.NORTH, actor);
            ListTag addresses = data.getList("addresses", Tag.TAG_STRING);
            require(snapshot.length == 6 && addresses.size() == 6, "Relay component addresses lost world-side alignment");
            int initialized = 0;
            for (Direction side : Direction.values()) {
                int index = side.get3DDataValue();
                require(Objects.equals(snapshot[index], addresses.getString(index)), "Relay component snapshot/address mismatch on " + side);
                if (snapshot[index] != null && !snapshot[index].isEmpty()) initialized++;
            }
            require(initialized > 0, "No actual relay component address initialized");
            double strength = ((Number) ((Object[]) relay.getClass().getMethod("getStrength", li.cil.oc.api.machine.Context.class, li.cil.oc.api.machine.Arguments.class).invoke(relay, new Object[]{null, null}))[0]).doubleValue();
            require(strength > 0 && Double.compare(strength, data.getDouble("signalStrength")) == 0, "Actual installed relay wireless card/strength differs from Jade data");
            pass("real relay wireless card/strength and " + initialized + " initialized component addresses retain their six world-side snapshot slots");
        }

        void requireNoDiskAddress(String phase) throws Exception {
            require(invoke(drive, "filesystemNode") == null, "Expected no mounted filesystem in " + phase);
            require(!JadePresent.read(drive, Direction.NORTH, actor).contains("address", Tag.TAG_STRING), "Jade exposed a drive/stale filesystem address in " + phase);
        }

        void finish() {
            dispose(); complete = true; active = null; limits();
        }

        void fail(Throwable error) {
            if (complete) return;
            RESULTS.add("FAIL Jade: " + error); LOGGER.error("JADE_CONTRACT FAIL", error);
            try { dispose(); } catch (Throwable cleanup) { LOGGER.error("JADE_CONTRACT cleanup failed", cleanup); }
            complete = true; active = null;
        }

        void dispose() {
            for (ItemEntity drop : drops) if (!drop.isRemoved()) drop.discard();
            drops.clear();
            if (computer instanceof Case hardware) hardware.machine().stop();
            var entries = new ArrayList<>(restored.entrySet());
            for (int index = entries.size() - 1; index >= 0; index--) {
                var entry = entries.get(index);
                if (level.getBlockEntity(entry.getKey()) instanceof Container inventory) inventory.clearContent();
                level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
            restored.clear();
            for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            forced.clear();
        }
    }

    /** Loaded only after ModList confirms Jade; absent-Jade startup links none of these types. */
    private static final class JadePresent {
        private static final String PROVIDER_ID = "opencomputers:information";

        static boolean registered(BlockEntity tile) {
            return snownee.jade.impl.WailaCommonRegistration.instance().getBlockNBTProviders(tile.getBlockState().getBlock(), tile)
                    .stream().anyMatch(provider -> PROVIDER_ID.equals(provider.getUid().toString()));
        }

        static CompoundTag read(BlockEntity tile, Direction side, FakePlayer player) {
            var providers = snownee.jade.impl.WailaCommonRegistration.instance().getBlockNBTProviders(tile.getBlockState().getBlock(), tile)
                    .stream().filter(provider -> PROVIDER_ID.equals(provider.getUid().toString())).toList();
            require(providers.size() == 1, "Expected exactly one actual automatically registered OC Jade provider");
            snownee.jade.api.BlockAccessor accessor = new snownee.jade.impl.BlockAccessorImpl.Builder()
                    .level(tile.getLevel()).player(player).serverConnected(true).showDetails(true).serverData(new CompoundTag())
                    .hit(new BlockHitResult(Vec3.atCenterOf(tile.getBlockPos()), side, tile.getBlockPos(), false))
                    .blockState(tile.getBlockState()).blockEntity(() -> tile).build();
            var provider = providers.getFirst();
            require(provider.shouldRequestData(accessor), "Real OC Jade provider refused its actual world block accessor");
            provider.appendServerData(accessor.getServerData(), accessor);
            require(accessor.getServerData().contains("opencomputers:waila", Tag.TAG_COMPOUND), "Actual OC Jade provider produced no data");
            return accessor.getServerData().getCompound("opencomputers:waila");
        }
    }
}
