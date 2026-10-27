package ocfixture;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Network;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded real robot crafting and vanilla villager transactions through the published SDK. */
public final class CraftingTradingContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-crafting-trading");
    private static Run active;
    private CraftingTradingContract() {}

    public static void start(ServerLevel level, BlockPos position, List<String> results) {
        if (active != null) {
            results.add("FAIL crafting.trading: a contract is already active");
            return;
        }
        Run run = new Run(level, position.immutable(), results);
        active = run;
        try { run.start(); }
        catch (Throwable error) { run.fail(error); active = null; clean(run); }
    }

    public static void tick() {
        Run run = active;
        if (run == null) return;
        try { if (!run.tick()) return; }
        catch (Throwable error) { run.fail(error); }
        active = null;
        clean(run);
    }

    public static boolean isRunning() { return active != null; }

    public static void close() {
        Run run = active;
        active = null;
        if (run != null) {
            run.fail(new IllegalStateException("server stopped before the real crafting/trading contract completed"));
            clean(run);
        }
    }

    private static void clean(Run run) {
        try { run.cleanup(); }
        catch (Throwable error) { run.results.add("FAIL crafting.trading cleanup: " + error); LOGGER.error("CRAFTING_TRADING cleanup failed", error); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ItemStack item(String name) {
        var descriptor = li.cil.oc.api.Items.get(name);
        require(descriptor != null, "Missing actual fixture item " + name);
        return descriptor.createItemStack(1);
    }

    private static int count(Container inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            if (inventory.getItem(slot).is(item)) total += inventory.getItem(slot).getCount();
        return total;
    }

    private static boolean same(ItemStack left, ItemStack right) {
        return left.getCount() == right.getCount() &&
            (left.isEmpty() && right.isEmpty() || ItemStack.isSameItemSameComponents(left, right));
    }

    private static final class Run {
        final ServerLevel level;
        final BlockPos position;
        final List<String> results;
        final Set<Long> forced = new LinkedHashSet<>();
        final Set<Integer> previousDrops = new HashSet<>();
        BlockState restored;
        Robot robot;
        Villager villager;
        MerchantOffer offer;
        TradeBridge bridge;
        int ticks, villagerAddedAt;
        boolean cleaned, failed, crafted;

        Run(ServerLevel level, BlockPos position, List<String> results) {
            this.level = level; this.position = position; this.results = results;
        }

        AABB bounds() { return new AABB(position).inflate(4); }

        void force(BlockPos pos) {
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true))
                forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            level.getChunkAt(pos);
        }

        void start() {
            force(position);
            force(position.east(2));
            require(level.isEmptyBlock(position) && level.getBlockEntity(position) == null,
                "Crafting/trading fixture refuses an occupied robot cell");
            for (Entity entity : level.getEntities((Entity) null, bounds(), entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb))
                previousDrops.add(entity.getId());
            require(CreativeTab.instance != null, "The actual configured creative robot tab is unavailable");
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
            ItemStack preset = null;
            ItemStack robotItem = item("robot");
            for (ItemStack stack : CreativeTab.instance.getDisplayItems()) {
                if (stack.is(robotItem.getItem()) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("oc:tier") == 3) {
                    preset = stack.copy(); break;
                }
            }
            require(preset != null && preset.getItem() instanceof BlockItem, "Creative tab has no actual tier-3 robot preset");
            restored = level.getBlockState(position);
            FakePlayer placer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "OC_CraftTrade"));
            placer.getAbilities().instabuild = true;
            placer.setPos(position.getX() + .5, position.getY(), position.getZ() - 2);
            placer.setItemInHand(InteractionHand.MAIN_HAND, preset);
            require(((BlockItem) preset.getItem()).place(new BlockPlaceContext(placer, InteractionHand.MAIN_HAND, preset,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false))).consumesAction(),
                "Actual creative robot failed normal BlockItem placement");
            require(level.getBlockEntity(position) instanceof Robot, "Placed block does not expose the published Robot interface");
            robot = (Robot) level.getBlockEntity(position);
            require(robot.machine() != null && robot.machine().host() instanceof Robot && !robot.machine().isRunning(),
                "Fixture must use the real stopped robot's Machine Context without starting firmware");
        }

        Component component(String name) {
            require(robot.machine().node().network() != null, "Actual robot has no initialized component network");
            for (Node node : robot.machine().node().network().nodes())
                if (node instanceof Component component && component.name().equals(name)) return component;
            throw new AssertionError("The real creative robot did not install " + name);
        }

        boolean tick() throws Exception {
            if (++ticks < 2) return false;
            require(!robot.machine().isRunning(), "The fixture unexpectedly started robot firmware");
            if (!crafted) {
                craft(); crafted = true;
                prepareTrade();
                // Chunk/entity visibility can lag addFreshEntity during newly forced chunk promotion.
                return false;
            }
            boolean tracked = level.getEntity(villager.getUUID()) == villager;
            boolean visible = level.getEntitiesOfClass(Villager.class, bounds()).contains(villager);
            require(!villager.isRemoved() && villager.isAlive(), "Fixture villager died or was removed before discovery");
            if (!tracked || !visible) {
                require(ticks - villagerAddedAt <= 80, "Fixture villager never became publicly tracked: UUID lookup=" + tracked +
                    ", AABB visible=" + visible + ", chunk=" + villager.chunkPosition());
                return false;
            }
            LOGGER.info("CRAFTING_TRADING villager ready after {} ticks; UUID lookup=true, AABB visible=true", ticks - villagerAddedAt);
            trade();
            require(!robot.machine().isRunning(), "Callbacks unexpectedly started robot firmware");
            results.add("LIMIT crafting.trading: one vanilla cake recipe and one two-input vanilla villager offer; batch/addon recipes, Trade cold recovery, multiplayer and client UI not exercised");
            return true;
        }

        void craft() throws Exception {
            Container inventory = robot.mainInventory();
            require(inventory != null && inventory.getContainerSize() >= 16, "Real creative robot has insufficient cargo slots");
            inventory.clearContent();
            robot.setSelectedSlot(15);
            for (int slot : new int[]{0, 1, 2}) inventory.setItem(slot, new ItemStack(Items.MILK_BUCKET));
            inventory.setItem(4, new ItemStack(Items.SUGAR));
            inventory.setItem(5, new ItemStack(Items.EGG));
            inventory.setItem(6, new ItemStack(Items.SUGAR));
            for (int slot : new int[]{8, 9, 10}) inventory.setItem(slot, new ItemStack(Items.WHEAT));
            Object[] crafted = component("crafting").invoke("craft", robot.machine(), 1);
            require(crafted != null && crafted.length == 2 && Boolean.TRUE.equals(crafted[0]) &&
                crafted[1] instanceof Number && ((Number) crafted[1]).intValue() == 1,
                "Real crafting callback did not report one successfully crafted cake");
            require(count(inventory, Items.CAKE) == 1 && inventory.getItem(15).is(Items.CAKE),
                "Real recipe output did not enter the selected robot cargo slot");
            require(count(inventory, Items.BUCKET) == 3 && count(inventory, Items.MILK_BUCKET) == 0 &&
                count(inventory, Items.SUGAR) == 0 && count(inventory, Items.EGG) == 0 && count(inventory, Items.WHEAT) == 0,
                "Real cake crafting did not consume the exact materials and return three empty buckets");
            int total = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) total += inventory.getItem(slot).getCount();
            require(total == 4, "Real cake crafting created unexpected extra inventory items");
            results.add("PASS crafting.real: stopped real Robot Machine Context + actual crafting Component.invoke crafts one vanilla cake, consumes 3 milk/2 sugar/1 egg/3 wheat, returns 3 buckets and inserts output at selected slot");
        }

        void prepareTrade() {
            Container inventory = robot.mainInventory();
            inventory.clearContent();
            inventory.setItem(0, new ItemStack(Items.EMERALD, 5));
            inventory.setItem(1, new ItemStack(Items.BREAD, 4));
            villager = EntityType.VILLAGER.create(level);
            require(villager != null, "Vanilla Villager factory failed");
            villager.setNoAi(true); villager.setNoGravity(true); villager.setInvulnerable(true);
            villager.setPos(robot.xPosition() + 1.25, robot.yPosition(), robot.zPosition());
            offer = new MerchantOffer(new ItemCost(Items.EMERALD, 2), Optional.of(new ItemCost(Items.BREAD, 3)),
                new ItemStack(Items.DIAMOND), 10, 3, 0f);
            MerchantOffers offers = new MerchantOffers(); offers.add(offer); villager.setOffers(offers);
            force(villager.blockPosition());
            require(level.addFreshEntity(villager), "Real vanilla Villager could not join the server level");
            villagerAddedAt = ticks;
            LOGGER.info("CRAFTING_TRADING villager added: UUID={}, position={}, chunk={}, UUID lookup={}, AABB visible={}",
                villager.getUUID(), villager.position(), villager.chunkPosition(), level.getEntity(villager.getUUID()) == villager,
                level.getEntitiesOfClass(Villager.class, bounds()).contains(villager));
        }

        void trade() throws Exception {
            Container inventory = robot.mainInventory();
            Object[] discovered = component("trading").invoke("getTrades", robot.machine());
            require(discovered != null && discovered.length == 1 && discovered[0] instanceof Object[],
                "Real trading callback did not return its trade userdata array");
            Value selected = null;
            for (Object entry : (Object[]) discovered[0]) {
                require(entry instanceof Value, "Trading discovery returned something other than a real SDK Value");
                CompoundTag saved = new CompoundTag();
                ((Value) entry).save(saved, level.registryAccess());
                if (saved.hasUUID("merchant") && saved.getUUID("merchant").equals(villager.getUUID())) {
                    require(selected == null, "Fixture villager unexpectedly exposes multiple offers");
                    selected = (Value) entry;
                }
            }
            require(selected != null, "Real trading discovery did not find the publicly tracked fixture Villager; returned trades=" +
                ((Object[]) discovered[0]).length + ", robot=" + new Vec3(robot.xPosition(), robot.yPosition(), robot.zPosition()) +
                ", villager=" + villager.position() + ", UUID lookup=" + (level.getEntity(villager.getUUID()) == villager));
            bridge = new TradeBridge(selected);
            Network.joinNewNetwork(bridge.node()); bridge.node().connect(robot.machine().node());
            Component callback = (Component) bridge.node();
            Object[] traded = callback.invoke("trade", robot.machine());
            require(traded != null && traded.length == 1 && Boolean.TRUE.equals(traded[0]),
                "Actual two-input villager offer did not complete through the real Trade callback");
            require(count(inventory, Items.EMERALD) == 3 && count(inventory, Items.BREAD) == 1 &&
                count(inventory, Items.DIAMOND) == 1 && offer.getUses() == 1,
                "Real trade did not deduct both costs, add exactly one output and increment offer uses once");
            results.add("PASS trading.real: actual vanilla Villager + actual discovered Trade Value consumes 2 emerald/3 bread, supplies 1 diamond and increments the real MerchantOffer uses exactly once");

            inventory.setItem(0, new ItemStack(Items.EMERALD, 5));
            inventory.setItem(1, new ItemStack(Items.BREAD, 2));
            List<ItemStack> before = new ArrayList<>();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) before.add(inventory.getItem(slot).copy());
            Object[] rejected = callback.invoke("trade", robot.machine());
            require(rejected != null && rejected.length == 2 && Boolean.FALSE.equals(rejected[0]) &&
                "not enough items to trade".equals(rejected[1]), "Missing second input did not produce the original trade error");
            for (int slot = 0; slot < before.size(); slot++)
                require(same(inventory.getItem(slot), before.get(slot)), "Failed second-input payment changed actual cargo slot " + slot);
            require(offer.getUses() == 1 && count(inventory, Items.DIAMOND) == 1,
                "Failed second-input payment changed actual merchant uses or supplied another result");
            results.add("PASS trading.atomic: with the real second input short by one, the actual Trade callback rejects both payment passes without changing any cargo ItemStack or merchant uses");
        }

        void fail(Throwable error) {
            if (failed) return;
            failed = true;
            results.add("FAIL crafting.trading: " + error);
            LOGGER.error("CRAFTING_TRADING FAIL", error);
        }

        void cleanup() {
            if (cleaned) return;
            cleaned = true;
            if (bridge != null) bridge.node().remove();
            if (villager != null) villager.discard();
            if (robot != null) robot.machine().stop();
            if (restored != null) {
                if (level.getBlockEntity(position) instanceof Container inventory) inventory.clearContent();
                level.setBlock(position, restored, 3);
            }
            for (Entity entity : level.getEntities((Entity) null, bounds(), entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb))
                if (!previousDrops.contains(entity.getId())) entity.discard();
            for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            forced.clear();
        }
    }

    /** SDK Value has no generic dispatcher; forward only this public annotated callback. */
    public static final class TradeBridge extends AbstractManagedEnvironment {
        private final Value value;
        private final Method method;

        TradeBridge(Value value) throws ReflectiveOperationException {
            this.value = value;
            method = value.getClass().getMethod("trade", Context.class, Arguments.class);
            require(method.getAnnotation(Callback.class) != null && method.getReturnType() == Object[].class,
                "Discovered Trade has no public SDK-annotated trade callback");
            setNode(Network.newNode(this, Visibility.Network).withComponent("oc_fixture_trade_bridge", Visibility.Network).create());
        }

        @Callback public Object[] trade(Context context, Arguments args) throws Exception {
            try { return (Object[]) method.invoke(value, context, args); }
            catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception exception) throw exception;
                if (cause instanceof Error fatal) throw fatal;
                throw new RuntimeException(cause);
            }
        }
    }
}
