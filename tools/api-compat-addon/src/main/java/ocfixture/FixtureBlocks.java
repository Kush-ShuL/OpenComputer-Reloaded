package ocfixture;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SimpleComponent;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.TileEntityEnvironment;
import li.cil.oc.api.prefab.TileEntitySidedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Real external prefab/SimpleComponent classes, registered by the addon rather than OC. */
public final class FixtureBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "oc_api_compat");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "oc_api_compat");
    private static final Supplier<Block> ENV = BLOCKS.register("environment", () -> new FixtureBlock(0));
    private static final Supplier<Block> SIDED = BLOCKS.register("sided", () -> new FixtureBlock(1));
    private static final Supplier<Block> SIMPLE = BLOCKS.register("simple", () -> new FixtureBlock(2));
    private static final Supplier<BlockEntityType<EnvironmentBlockEntity>> ENV_TYPE = TYPES.register("environment", () -> BlockEntityType.Builder.of(EnvironmentBlockEntity::new, ENV.get()).build(null));
    private static final Supplier<BlockEntityType<SidedBlockEntity>> SIDED_TYPE = TYPES.register("sided", () -> BlockEntityType.Builder.of(SidedBlockEntity::new, SIDED.get()).build(null));
    private static final Supplier<BlockEntityType<SimpleBlockEntity>> SIMPLE_TYPE = TYPES.register("simple", () -> BlockEntityType.Builder.of(SimpleBlockEntity::new, SIMPLE.get()).build(null));
    private static final List<PendingRemoval> REMOVALS = new ArrayList<>();
    private FixtureBlocks() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); TYPES.register(bus); }
    public static void verifyPrefabs(ServerLevel level) {
        BlockPos position = new BlockPos(0, level.getMinBuildHeight(), 0);
        EnvironmentBlockEntity original = new EnvironmentBlockEntity(position, ENV.get().defaultBlockState());
        EnvironmentBlockEntity restored = new EnvironmentBlockEntity(position, ENV.get().defaultBlockState());
        SidedBlockEntity sided = new SidedBlockEntity(position, SIDED.get().defaultBlockState());
        SidedBlockEntity copy = new SidedBlockEntity(position, SIDED.get().defaultBlockState());
        try {
            Network.joinNewNetwork(original.node());
            CompoundTag saved = original.saveForContract(level.registryAccess());
            restored.loadForContract(saved, level.registryAccess());
            require(original.node().address().equals(restored.node().address()), "TileEntityEnvironment lost its node address");
            original.onChunkUnloaded();
            require(original.node().network() == null, "TileEntityEnvironment did not detach on chunk unload");
            for (Direction side : new Direction[]{Direction.DOWN, Direction.EAST}) Network.joinNewNetwork(sided.sidedNode(side));
            copy.loadForContract(sided.saveForContract(level.registryAccess()), level.registryAccess());
            for (Direction side : new Direction[]{Direction.DOWN, Direction.EAST}) require(sided.sidedNode(side).address().equals(copy.sidedNode(side).address()), "Sided prefab lost " + side + " address");
            require(copy.sidedNode(Direction.NORTH) == null, "Sided prefab exposed an absent side");
            sided.onChunkUnloaded();
            for (Direction side : new Direction[]{Direction.DOWN, Direction.EAST}) require(sided.sidedNode(side).network() == null, "Sided prefab did not detach " + side);
        } finally {
            original.setRemoved(); restored.setRemoved(); sided.setRemoved(); copy.setRemoved();
        }
    }
    public static void verifySimpleComponent(ServerLevel level) throws Exception {
        BlockPos origin = new BlockPos(0, level.getMaxBuildHeight() - 16, 0), adjacent = origin.east();
        require(level.isEmptyBlock(origin) && level.isEmptyBlock(adjacent), "World fixture requires two empty cells; nothing was overwritten");
        try {
            level.setBlock(origin, ENV.get().defaultBlockState(), 3);
            level.setBlock(adjacent, SIMPLE.get().defaultBlockState(), 3);
            EnvironmentBlockEntity environment = (EnvironmentBlockEntity) level.getBlockEntity(origin);
            SimpleBlockEntity simple = (SimpleBlockEntity) level.getBlockEntity(adjacent);
            Network.joinOrCreateNetwork(environment); Network.joinOrCreateNetwork(simple);
            Component found = null;
            for (Node node : environment.node().network().nodes()) if (node instanceof Component component && component.name().equals("fixture_simple")) found = component;
            require(found != null, "An external SimpleComponent block did not join its neighbor's network");
            Object[] result = found.invoke("ping", new FixtureContext(environment.node()));
            require(result.length == 1 && result[0].equals("simple-pong"), "SimpleComponent did not dispatch its public callback");
            require(simple.saveWithFullMetadata(level.registryAccess()).toString().contains(found.address()), "SimpleComponent attachment did not persist its address");
            REMOVALS.add(new PendingRemoval(found));
            BaseContract.pass("placed external SimpleComponent discovers callbacks, connects and persists its address");
        } finally { level.removeBlock(adjacent, false); level.removeBlock(origin, false); }
    }
    public static void tick() {
        for (var iterator = REMOVALS.iterator(); iterator.hasNext();) {
            PendingRemoval removal = iterator.next();
            if (--removal.remaining > 0) continue;
            if (removal.node.network() == null) BaseContract.pass("removed SimpleComponent attachment detaches from the network");
            else { BaseContract.fail("removed SimpleComponent attachment retained a live node"); removal.node.remove(); }
            iterator.remove();
        }
    }
    public static boolean isRunning() { return !REMOVALS.isEmpty(); }
    public static void close() { for (PendingRemoval removal : REMOVALS) removal.node.remove(); REMOVALS.clear(); }
    private static void require(boolean condition, String description) { if (!condition) throw new AssertionError(description); }
    private static final class PendingRemoval { final Node node; int remaining = 3; PendingRemoval(Node node) { this.node = node; } }
    private static final class FixtureBlock extends BaseEntityBlock {
        private final int kind;
        FixtureBlock(int kind) { super(BlockBehaviour.Properties.of().strength(1)); this.kind = kind; }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
        @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return switch (kind) { case 0 -> new EnvironmentBlockEntity(position, state); case 1 -> new SidedBlockEntity(position, state); default -> new SimpleBlockEntity(position, state); }; }
    }
    public static final class EnvironmentBlockEntity extends TileEntityEnvironment {
        EnvironmentBlockEntity(BlockPos position, BlockState state) { super(ENV_TYPE.get(), position, state); node = Network.newNode(this, Visibility.Network).withComponent("fixture_prefab").withConnector(64).create(); }
        CompoundTag saveForContract(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); saveAdditional(tag, registries); return tag; }
        void loadForContract(CompoundTag tag, HolderLookup.Provider registries) { loadAdditional(tag, registries); }
        @Callback(direct = true) public Object[] ping(Context context, Arguments args) { return new Object[]{"prefab-pong"}; }
    }
    public static final class SidedBlockEntity extends TileEntitySidedEnvironment implements Environment {
        SidedBlockEntity(BlockPos position, BlockState state) {
            super(SIDED_TYPE.get(), position, state);
            nodes[Direction.DOWN.get3DDataValue()] = Network.newNode(this, Visibility.Network).withConnector(32).create();
            nodes[Direction.EAST.get3DDataValue()] = Network.newNode(this, Visibility.Network).withConnector(64).create();
        }
        public Node node() { return null; }
        public void onConnect(Node node) {} public void onDisconnect(Node node) {} public void onMessage(Message message) {}
        @OnlyIn(Dist.CLIENT) public boolean canConnect(Direction side) { return side == Direction.DOWN || side == Direction.EAST; }
        CompoundTag saveForContract(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); saveAdditional(tag, registries); return tag; }
        void loadForContract(CompoundTag tag, HolderLookup.Provider registries) { loadAdditional(tag, registries); }
    }
    public static final class SimpleBlockEntity extends BlockEntity implements SimpleComponent {
        SimpleBlockEntity(BlockPos position, BlockState state) { super(SIMPLE_TYPE.get(), position, state); }
        @Override public String getComponentName() { return "fixture_simple"; }
        @Callback(direct = true) public Object[] ping(Context context, Arguments args) { return new Object[]{"simple-pong"}; }
    }
    private record FixtureContext(Node node) implements Context {
        public boolean canInteract(String player) { return true; } public boolean isRunning() { return false; } public boolean isPaused() { return false; }
        public boolean start() { return false; } public boolean stop() { return false; } public boolean pause(double seconds) { return false; }
        public void consumeCallBudget(double cost) {} public boolean signal(String name, Object... args) { return false; }
    }
}
