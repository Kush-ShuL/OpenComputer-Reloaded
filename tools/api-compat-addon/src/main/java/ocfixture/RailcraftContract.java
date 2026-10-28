package ocfixture;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.internal.Adapter;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Official physical Worldspikes/crowbar, actual Adapter callbacks and persistent NeoForge tickets. */
public final class RailcraftContract {
    private static final Logger LOG = LoggerFactory.getLogger("oc-railcraft-contract");
    private static Run active;
    private RailcraftContract() {}

    public static void start(ServerLevel level, BlockPos position, List<String> results) {
        require(active == null, "Railcraft contract is already running");
        Run run = new Run(level, position.immutable(), results);
        active = run;
        try { run.start(); } catch (Throwable error) { run.fail(error); active = null; run.cleanup(); }
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        Run run = active;
        if (run == null) return;
        try { if (!run.tick()) return; } catch (Throwable error) { run.fail(error); }
        active = null;
        run.cleanup();
    }
    public static void close() {
        Run run = active;
        active = null;
        if (run != null) { run.fail(new AssertionError("Server stopped before Railcraft completed")); run.cleanup(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static ItemStack item(String name) {
        var descriptor = li.cil.oc.api.Items.get(name);
        require(descriptor != null, "Missing actual OC Railcraft fixture material " + name);
        return descriptor.createItemStack(1);
    }
    private static ResourceLocation railcraft(String name) { return ResourceLocation.fromNamespaceAndPath("railcraft", name); }

    private static final class Run {
        final ServerLevel level;
        final BlockPos position, adapterPos, worldPos, personalPos, splitterPos, powerPos;
        final List<String> results;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final boolean enabled;
        Case computer;
        Adapter adapter;
        FakePlayer actor;
        ItemStack crowbar;
        Component world, personal;
        Access tools;
        int ticks, phase;

        Run(ServerLevel level, BlockPos position, List<String> results) {
            require(API.config != null && ModList.get().isLoaded("railcraft"), "Railcraft proof requires its official runtime and actual OC configuration");
            this.level = level; this.position = position; this.results = results;
            adapterPos = position.east(); worldPos = position.east(2); personalPos = adapterPos.south();
            splitterPos = position.south(3); powerPos = worldPos.above();
            enabled = API.config.getStringList("integration.modBlacklist").stream().noneMatch(id -> id.trim().equalsIgnoreCase("railcraft"));
        }

        void remember(BlockPos pos) {
            if (restored.containsKey(pos)) return;
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            level.getChunkAt(pos);
            require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Railcraft proof refuses occupied cell " + pos);
            restored.put(pos, level.getBlockState(pos));
        }
        void place(BlockPos pos, Block block) {
            remember(pos);
            require(level.setBlock(pos, block.defaultBlockState(), 3), "Could not place actual Railcraft fixture block " + pos);
        }
        Block foreignBlock(String name) {
            ResourceLocation id = railcraft(name);
            Block block = BuiltInRegistries.BLOCK.get(id);
            require(id.equals(BuiltInRegistries.BLOCK.getKey(block)), "Official Railcraft block is unavailable: " + id);
            return block;
        }
        void start() throws ReflectiveOperationException {
            place(position, li.cil.oc.api.Items.get("case3").block());
            place(adapterPos, li.cil.oc.api.Items.get("adapter").block());
            place(splitterPos, li.cil.oc.api.Items.get("netsplitter").block());
            require(level.getBlockEntity(position) instanceof Case && level.getBlockEntity(adapterPos) instanceof Adapter, "Fixture devices lack their real published SDK hosts");
            computer = (Case) level.getBlockEntity(position); adapter = (Adapter) level.getBlockEntity(adapterPos);
            actor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "OC_Railcraft"));
            actor.setGameMode(GameType.SURVIVAL);
            actor.setPos(adapterPos.getX() + .5, adapterPos.getY(), adapterPos.getZ() - 2);
            place(worldPos, foreignBlock("world_spike"));
            remember(personalPos); remember(powerPos);
            Block personalBlock = foreignBlock("personal_world_spike");
            ItemStack stack = new ItemStack(personalBlock);
            require(stack.getItem() instanceof BlockItem, "Personal Worldspike has no actual BlockItem");
            actor.setItemInHand(InteractionHand.MAIN_HAND, stack);
            require(((BlockItem) stack.getItem()).place(new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(personalPos), Direction.UP, personalPos, false))).consumesAction(), "Actual personal Worldspike placement failed");
            require(level.getBlockState(personalPos).is(personalBlock), "BlockItem did not place the real personal Worldspike");
            ResourceLocation id = railcraft("iron_crowbar");
            var tool = BuiltInRegistries.ITEM.get(id);
            require(id.equals(BuiltInRegistries.ITEM.getKey(tool)), "Official Railcraft iron crowbar is unavailable");
            crowbar = new ItemStack(tool);
            require(crowbar.isDamageableItem(), "Actual iron crowbar lacks its official durability");
            actor.setItemInHand(InteractionHand.MAIN_HAND, crowbar);
            tools = new Access();
        }

        Object[] invoke(Component component, String method) throws Exception { return component.invoke(method, computer.machine()); }
        Object single(Component component, String method) throws Exception {
            Object[] values = invoke(component, method);
            require(values != null && values.length == 1, "Actual " + method + " did not return exactly one value");
            return values[0];
        }
        String string(Object value) { return value instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : String.valueOf(value); }
        List<Component> components() {
            List<Component> values = new ArrayList<>();
            for (Node node : computer.machine().node().network().nodes()) if (node instanceof Component component && "worldspike".equals(component.name()) && component.canBeSeenFrom(computer.machine().node())) values.add(component);
            return values;
        }
        Optional<GameProfile> owner(BlockPos pos) throws ReflectiveOperationException {
            Class<?> contract = Class.forName("mods.railcraft.api.core.Ownable");
            Object tile = level.getBlockEntity(pos);
            require(contract.isInstance(tile), "Actual Worldspike does not expose its official Ownable contract");
            @SuppressWarnings("unchecked") Optional<GameProfile> profile = (Optional<GameProfile>) contract.getMethod("getOwner").invoke(tile);
            return profile;
        }
        void callbacks() throws Exception {
            require(owner(worldPos).isEmpty(), "Unowned real world spike unexpectedly has a profile");
            GameProfile actualOwner = owner(personalPos).orElseThrow(() -> new AssertionError("Real BlockItem placement did not assign a personal owner"));
            require(actualOwner.equals(actor.getGameProfile()), "Actual personal Worldspike retained the wrong placing owner");
            for (Component component : components()) {
                String type = string(single(component, "getType"));
                if (type.equals("world")) { require(world == null, "Actual Adapter mounted duplicate world spikes"); world = component; }
                else if (type.equals("personal")) { require(personal == null, "Actual Adapter mounted duplicate personal spikes"); personal = component; }
                else throw new AssertionError("Actual official spike returned unexpected type " + type);
                Object fuel = single(component, "getFuel");
                require(fuel instanceof Number number && number.intValue() == 0, "A fuel-free actual modern Worldspike returned nonzero fuel");
                Object[] slot = invoke(component, "getFuelSlotContents");
                require(slot != null && slot.length == 0, "Actual fuel-free Worldspike invented a fuel slot");
                require(Boolean.FALSE.equals(single(component, "isDisabled")), "Actual always-loading Worldspike reported disabled");
            }
            require(world != null && personal != null, "Actual Adapter lacks one of the two official spike variants");
            Object[] noOwner = invoke(world, "getOwner");
            require(noOwner != null && noOwner.length == 0, "Real unowned world spike did not return the original empty owner result");
            require(actualOwner.getName().equals(string(single(personal, "getOwner"))), "Actual owner callback differs from the officially placed entity profile");
            for (BlockPos pos : List.of(worldPos, personalPos)) {
                require(!(level.getBlockEntity(pos) instanceof Container) && level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) == null, "Official modern Worldspike unexpectedly exposes a real inventory");
            }
            pass("callbacks actual Case/Adapter invokes all five original callbacks on official world/personal spikes; real placing Owner, empty unowned result, actual absent inventory and fuel-free mapping");
        }

        Set<Long> tickets(BlockPos owner) {
            ForcedChunksSavedData data = level.getDataStorage().get(ForcedChunksSavedData.factory(), ForcedChunksSavedData.FILE_ID);
            require(data != null, "Official Worldspike created no actual persistent chunk data");
            CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
            Set<Long> found = new HashSet<>();
            for (Tag value : saved.getList("ModForced", Tag.TAG_COMPOUND)) {
                CompoundTag controller = (CompoundTag) value;
                if (!"railcraft:ticket_controller".equals(controller.getString("Controller"))) continue;
                for (Tag raw : controller.getList("ModForced", Tag.TAG_COMPOUND)) {
                    CompoundTag chunk = (CompoundTag) raw;
                    for (Tag entry : chunk.getList("Blocks", Tag.TAG_COMPOUND)) {
                        CompoundTag block = (CompoundTag) entry;
                        if (owner.equals(new BlockPos(block.getInt("X"), block.getInt("Y"), block.getInt("Z")))) found.add(chunk.getLong("Chunk"));
                    }
                }
            }
            return found;
        }
        void nine(BlockPos pos) {
            ChunkPos center = new ChunkPos(pos);
            Set<Long> expected = new HashSet<>();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) expected.add(ChunkPos.asLong(center.x + dx, center.z + dz));
            require(tickets(pos).equals(expected), "Official Worldspike's real persistent tickets differ from its nine chunks at " + pos + ": " + tickets(pos));
        }

        void wrench() throws ReflectiveOperationException {
            ItemStack before = actor.getMainHandItem().copy();
            require(tools.check(before) == enabled && tools.use(actor, splitterPos, false) == enabled, "Actual registered Railcraft wrench check/simulation ignored availability");
            require(ItemStack.matches(before, actor.getMainHandItem()), "Actual non-consuming Crowbar hook changed its item");
            require(tools.use(actor, splitterPos, true) == enabled, "Actual consuming Crowbar hook ignored availability");
            require(actor.getMainHandItem().getCount() == before.getCount() && actor.getMainHandItem().getDamageValue() == before.getDamageValue() + (enabled ? 1 : 0), "Official onWhack did not perform exactly one real durability cost");
            actor.setShiftKeyDown(false);
            BlockState state = level.getBlockState(splitterPos);
            var property = state.getBlock().getStateDefinition().getProperty("east");
            require(property instanceof BooleanProperty, "Real NetSplitter lacks its east face");
            boolean open = state.getValue((BooleanProperty) property);
            int damage = actor.getMainHandItem().getDamageValue();
            ItemInteractionResult result = state.useItemOn(actor.getMainHandItem(), level, actor, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(splitterPos), Direction.EAST, splitterPos, false));
            require(result == (enabled ? ItemInteractionResult.sidedSuccess(false) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION)
                    && level.getBlockState(splitterPos).getValue((BooleanProperty) property) == (open ^ enabled), "Actual OC block Crowbar interaction returned or changed the wrong face");
            require(actor.getMainHandItem().getDamageValue() == damage + (enabled ? 1 : 0), "Actual OC block caller missed or duplicated official Crowbar damage");
            actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            require(!tools.check(actor.getMainHandItem()) && !tools.use(actor, splitterPos, false) && !tools.use(actor, splitterPos, true), "Actual ordinary stone was accepted as a Railcraft Crowbar");
            pass("crowbar real official iron crowbar registered check/usage(false/true), one actual onWhack cost and actual NetSplitter use; stone rejection; enabled=" + enabled);
        }

        boolean tick() throws Exception {
            require(++ticks <= 100, "Railcraft fixture exceeded actual world tick bound at phase " + phase);
            if (ticks < 3 || computer.machine().node().network() == null || adapter.node().network() != computer.machine().node().network()) return false;
            if (phase == 0) {
                List<Component> values = components();
                if (enabled && values.size() < 2) return false;
                require(values.size() == (enabled ? 2 : 0), "Actual Adapter mounted an unexpected number of Worldspike components");
                require((Driver.driverFor(level, worldPos, Direction.WEST) != null) == enabled
                    && (Driver.driverFor(level, personalPos, Direction.NORTH) != null) == enabled, "Real block driver registry ignored the Railcraft blacklist");
                nine(worldPos); nine(personalPos);
                if (enabled) callbacks();
                else pass("blacklist real official spikes retain their own tickets but actual Adapter/SDK driver registry exposes no worldspike callbacks");
                wrench();
                require(level.setBlock(powerPos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3), "Could not supply a real redstone neighbor to the Worldspike");
                phase = 1;
                return false;
            }
            if (phase == 1) {
                require(level.hasNeighborSignal(worldPos), "The actual Worldspike never received real redstone power");
                nine(worldPos); nine(personalPos);
                if (enabled) require(Boolean.FALSE.equals(single(world, "isDisabled")), "Modern redstone-independent official spike falsely reported disabled");
                require(level.setBlock(powerPos, Blocks.AIR.defaultBlockState(), 3), "Could not remove owned redstone source");
                phase = 2;
                return false;
            }
            if (phase == 2) {
                require(!level.hasNeighborSignal(worldPos), "Owned Worldspike remains powered after actual source removal");
                nine(worldPos); nine(personalPos);
                if (enabled) require(Boolean.FALSE.equals(single(world, "isDisabled")), "Unpowered modern spike falsely reported disabled");
                pass("tickets official Worldspikes hold actual nine-chunk controller tickets without fuel, through real redstone power and removal; modern isDisabled=false reflects unchanged tickets");
                require(level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), 3), "Could not remove owned official world spike");
                phase = 3;
                return false;
            }
            require(tickets(worldPos).isEmpty(), "Removing the actual world spike left its persistent tickets behind");
            nine(personalPos);
            if (enabled) {
                if (components().size() > 1) return false;
                require(world.network() == null && components().size() == 1 && components().getFirst() == personal, "Actual Adapter failed to detach only the removed spike environment");
            }
            pass("remove actual official block removal clears only its ticket owner and Adapter component; personal spike remains connected and keeps its own nine tickets");
            results.add("LIMIT railcraft: official 1.2.15 world/personal variants and original five callbacks/tools exercised; old admin/passive have no modern blocks, no invented fuel/redstone mechanism; no client/multiplayer or absent-mod startup is claimed");
            return true;
        }

        void pass(String message) { results.add("PASS railcraft." + message); }
        void fail(Throwable error) { results.add("FAIL railcraft phase=" + phase + ": " + error); LOG.error("RAILCRAFT_CONTRACT phase {} failed", phase, error); }
        void cleanup() {
            if (actor != null) actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var entries = new ArrayList<>(restored.entrySet()); Collections.reverse(entries);
            for (var entry : entries) level.setBlock(entry.getKey(), entry.getValue(), 3);
            restored.clear();
            for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            forced.clear();
        }
    }

    /** Only invokes the existing public wrench registry; the official tool owns its actual behavior. */
    private static final class Access {
        final Method checks, use;
        Access() throws ReflectiveOperationException {
            Class<?> registry = Class.forName("li.cil.oc.common.integration.WrenchTools");
            checks = registry.getMethod("checks");
            use = registry.getMethod("use", Player.class, BlockPos.class, boolean.class);
        }
        @SuppressWarnings("unchecked") boolean check(ItemStack stack) throws ReflectiveOperationException {
            for (Predicate<ItemStack> check : (List<Predicate<ItemStack>>) checks.invoke(null)) if (check.test(stack)) return true;
            return false;
        }
        boolean use(Player player, BlockPos pos, boolean damage) throws ReflectiveOperationException {
            try { return Boolean.TRUE.equals(use.invoke(null, player, pos, damage)); }
            catch (InvocationTargetException error) { throw new IllegalStateException("Actual registered Crowbar callback failed", error.getCause()); }
        }
    }
}
