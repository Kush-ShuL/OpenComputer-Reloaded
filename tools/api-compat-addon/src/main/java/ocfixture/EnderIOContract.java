package ocfixture;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import li.cil.oc.api.API;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real registered Yeta and actual registered OC wrench callbacks/block interaction. */
public final class EnderIOContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-enderio-contract");
    private static Run active;
    private EnderIOContract() {}

    public static void start(ServerLevel level, BlockPos position, List<String> results) throws Exception {
        require(active == null, "An EnderIO wrench contract is already running");
        Run run = new Run(level, position.immutable(), results); active = run;
        try { run.start(); }
        catch (Exception | Error error) { run.fail(error); throw error; }
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        Run run = active; if (run == null) return;
        try {
            if (++run.ticks < 3) return; // Actual block entities get their normal world tickers first.
            run.verify(); active = null; run.cleanup();
        } catch (Throwable error) { run.fail(error); }
    }
    public static void close() {
        if (active != null) active.fail(new AssertionError("Server stopped before the EnderIO wrench contract completed"));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class Run {
        final ServerLevel level;
        final BlockPos position, waypoint;
        final List<String> results;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final Access access;
        final boolean allowed;
        FakePlayer actor;
        ItemStack wrench;
        int ticks;

        Run(ServerLevel level, BlockPos position, List<String> results) throws ReflectiveOperationException {
            require(API.config != null, "OC SDK configuration is unavailable");
            require(ModList.get().isLoaded("enderio"), "Install the official real EnderIO runtime for this scenario");
            this.level = level; this.position = position; this.waypoint = position.east(2); this.results = results;
            allowed = !API.config.getStringList("integration.modBlacklist").stream().anyMatch(id -> id.trim().equalsIgnoreCase("enderio"));
            access = new Access();
        }

        void start() {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("enderio", "yeta_wrench");
            var actual = BuiltInRegistries.ITEM.get(id);
            require(id.equals(BuiltInRegistries.ITEM.getKey(actual)), "Official enderio:yeta_wrench is not registered");
            wrench = new ItemStack(actual);
            require(!wrench.isEmpty() && !wrench.isDamageableItem(), "Selected official modern Yeta does not have its expected non-damageable state");
            place(position, "netsplitter"); place(waypoint, "waypoint");
            actor = new FakePlayer(level, new GameProfile(UUID.fromString("6f2e9933-d9a1-4b9e-9689-1e283b0d0a42"), "OC_EnderIO"));
            actor.getAbilities().instabuild = false;
            actor.setPos(position.getX() + .5, position.getY() + .5, position.getZ() - 2);
            actor.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }

        void place(BlockPos pos, String name) {
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            level.getChunkAt(pos);
            require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "EnderIO contract will not overwrite occupied cell " + pos);
            var descriptor = li.cil.oc.api.Items.get(name);
            require(descriptor != null && descriptor.block() != null, "SDK lacks real OC " + name);
            restored.put(pos, level.getBlockState(pos));
            require(level.setBlock(pos, descriptor.block().defaultBlockState(), 3), "Could not place actual OC " + name);
        }

        void verify() {
            ItemStack before = actor.getMainHandItem().copy();
            // Inspect the real registered check callbacks. WrenchTools.isWrench's
            // separate generic tag fallback is not the optional EnderIO callback.
            require(access.registeredCheck(actor.getMainHandItem()) == allowed, "Registered OC wrench-check callback did not honor EnderIO availability/blacklist");
            require(access.use(actor, position, false) == allowed, "Registered OC wrench usage simulation returned the wrong result");
            sameItem(before, "changeDurability=false modified actual Yeta state");
            require(access.use(actor, position, true) == allowed, "Registered OC wrench actual-use callback returned the wrong result");
            sameItem(before, "changeDurability=true consumed or damaged the real non-damageable modern Yeta");
            pass("actual WrenchTools registered check and usage(false/true) honor EnderIO configuration; real Yeta count/components remain unchanged; enabled=" + allowed);

            actor.setShiftKeyDown(false);
            BlockState original = level.getBlockState(waypoint);
            ItemInteractionResult result = original.useItemOn(actor.getMainHandItem(), level, actor, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(waypoint), Direction.NORTH, waypoint, false));
            require(result == expected(allowed) && level.getBlockState(waypoint).equals(original), "Actual same-axis Waypoint interaction failed its non-consuming check");
            sameItem(before, "Waypoint non-consuming detection modified Yeta");
            interact(false, allowed); interact(true, allowed);
            sameItem(before, "Real NetSplitter use modified modern Yeta item state");
            pass("actual BlockState.useItemOn: Waypoint same-axis detection; NetSplitter "
                + (allowed ? "normal east/sneaking west toggle only their intended faces" : "preserves all six faces under the blacklist")
                + "; Yeta remains unchanged");

            actor.setShiftKeyDown(false);
            actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            ItemStack ordinary = actor.getMainHandItem().copy();
            require(!access.registeredCheck(ordinary) && !access.use(actor, position, false) && !access.use(actor, position, true), "Ordinary stone was accepted by a registered wrench callback");
            interact(false, false);
            sameItem(ordinary, "Rejected ordinary item was modified");
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            actor.setItemInHand(InteractionHand.OFF_HAND, wrench);
            require(!access.registeredCheck(ItemStack.EMPTY) && !access.use(actor, position, false) && !access.use(actor, position, true), "Offhand-only Yeta bypassed the original main-hand contract");
            require(ItemStack.matches(before, actor.getOffhandItem()), "Rejected offhand Yeta was modified");
            pass("real ordinary item and offhand-only Yeta are rejected by both registered callback paths");
            results.add("LIMIT EnderIO: this proves the two original wrench hooks and their real OC block callers; unrelated EnderIO systems, client rendering and remote multiplayer are not exercised");
        }

        void interact(boolean sneak, boolean accepted) {
            actor.setShiftKeyDown(sneak);
            BlockState original = level.getBlockState(position);
            Map<Direction, Boolean> before = faces(original);
            ItemInteractionResult result = original.useItemOn(actor.getMainHandItem(), level, actor, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.EAST, position, false));
            require(result == expected(accepted), "Actual NetSplitter returned the wrong interaction result, sneak=" + sneak + ": " + result);
            BlockState current = level.getBlockState(position);
            require(current.is(original.getBlock()), "Wrench interaction replaced the actual NetSplitter");
            Map<Direction, Boolean> after = faces(current);
            Direction changed = sneak ? Direction.WEST : Direction.EAST;
            for (Direction side : Direction.values()) require(after.get(side) == (before.get(side) ^ (accepted && side == changed)),
                "Actual NetSplitter changed the wrong face: " + side + ", sneak=" + sneak);
        }

        static ItemInteractionResult expected(boolean accepted) {
            return accepted ? ItemInteractionResult.sidedSuccess(false) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        static Map<Direction, Boolean> faces(BlockState state) {
            Map<Direction, Boolean> values = new EnumMap<>(Direction.class);
            for (Direction side : Direction.values()) {
                var property = state.getBlock().getStateDefinition().getProperty(side.getName());
                require(property instanceof BooleanProperty, "Actual NetSplitter has no " + side.getName() + " state");
                values.put(side, state.getValue((BooleanProperty) property));
            }
            return values;
        }
        void sameItem(ItemStack before, String message) { require(ItemStack.matches(before, actor.getMainHandItem()), message); }
        void pass(String message) { results.add("PASS EnderIO: " + message); LOGGER.info("ENDERIO_CONTRACT PASS {}", message); }
        void fail(Throwable error) {
            active = null; results.add("FAIL EnderIO: " + error); LOGGER.error("ENDERIO_CONTRACT FAIL", error);
            try { cleanup(); } catch (Throwable cleanup) { LOGGER.error("ENDERIO_CONTRACT cleanup failed", cleanup); }
        }
        void cleanup() {
            if (actor != null) { actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); }
            var entries = new ArrayList<>(restored.entrySet()); Collections.reverse(entries);
            for (var entry : entries) level.setBlock(entry.getKey(), entry.getValue(), 3);
            restored.clear();
            for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            forced.clear();
        }
    }

    /** Uses actual public registry callbacks without compile-time OC implementation linkage. */
    private static final class Access {
        final Method checks, use;
        Access() throws ReflectiveOperationException {
            Class<?> tools = Class.forName("li.cil.oc.common.integration.WrenchTools", false, EnderIOContract.class.getClassLoader());
            checks = tools.getMethod("checks");
            use = tools.getMethod("use", Player.class, BlockPos.class, boolean.class);
        }
        @SuppressWarnings("unchecked")
        boolean registeredCheck(ItemStack stack) {
            List<Predicate<ItemStack>> callbacks = (List<Predicate<ItemStack>>) call(checks);
            return callbacks.stream().anyMatch(callback -> callback.test(stack));
        }
        boolean use(Player player, BlockPos position, boolean changeDurability) {
            return Boolean.TRUE.equals(call(use, player, position, changeDurability));
        }
        static Object call(Method method, Object... arguments) {
            try { return method.invoke(null, arguments); }
            catch (IllegalAccessException error) { throw new IllegalStateException("Public OC wrench registry is inaccessible", error); }
            catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error fatal) throw fatal;
                throw new IllegalStateException("Actual registered OC wrench callback failed", cause);
            }
        }
    }
}
