package li.cil.oc.common.integration.appeng;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import com.google.common.collect.ImmutableSet;
import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import li.cil.oc.OpenComputers;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.prefab.AbstractValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Real asynchronous crafting, with a dedicated public-API requester node for restorable links. */
public final class Craftable extends AbstractValue implements ICraftingRequester {
    private static final Set<Craftable> ACTIVE = ConcurrentHashMap.newKeySet();
    private final Set<ICraftingLink> links = new CopyOnWriteArraySet<>();
    private AEUtil.Target target;
    private AEItemKey key;
    private long outputAmount = 1;
    private IManagedGridNode managed;
    private IGridConnection connection;
    private CompoundTag savedNode;
    private HolderLookup.Provider registries;
    private String dimension;
    private BlockPos position;
    private Direction side;
    private boolean loading;
    private int retry = 1, delay, calculating;

    public Craftable() {}
    Craftable(AEUtil.Target target, AEItemKey key, long amount) {
        this.target = target; this.key = key; this.outputAmount = amount;
        registries = target.level().registryAccess(); dimension = target.level().dimension().location().toString();
        position = target.position(); side = target.partSide();
    }
    @Override public ImmutableSet<ICraftingLink> getRequestedJobs() { return ImmutableSet.copyOf(links); }
    @Override public IGridNode getActionableNode() { return managed == null ? null : managed.getNode(); }
    @Override public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        // As in the original integration, leave the final output to AE's network storage.
        return 0;
    }
    @Override public void jobStateChange(ICraftingLink link) { links.remove(link); }
    private String unavailable() {
        if (loading) return "waiting for ae network to load";
        if (target == null) return "no controller";
        try { target.gridNode(); return null; } catch (IllegalStateException error) { return error.getMessage(); }
    }
    @Callback(doc="function():table -- Returns the item stack representation of the crafting result.")
    public Object[] getItemStack(Context context, Arguments args) { return new Object[]{key.toStack((int)Math.min(Integer.MAX_VALUE, Math.max(1, outputAmount)))}; }
    @Callback(doc="function():number -- Returns the number of requests in progress.")
    public Object[] requesting(Context context, Arguments args) {
        String unavailable = unavailable();
        return unavailable == null ? new Object[]{target.grid().getCraftingService().getRequestedAmount(key)} : new Object[]{null, unavailable};
    }
    @Callback(doc="function([amount:int=1, prioritizePower:boolean=true, cpuName:string]):userdata -- Request crafting and return the real job status.")
    public Object[] request(Context context, Arguments args) {
        String unavailable = unavailable(); if (unavailable != null) return new Object[]{null, unavailable};
        int count = args.optInteger(0, 1); if (count < 1) throw new IllegalArgumentException("invalid amount");
        boolean prioritize = args.optBoolean(1, true); String cpuName = args.optString(2, "");
        var crafting = target.grid().getCraftingService();
        ICraftingCPU selected = null;
        if (!cpuName.isEmpty()) for (var cpu : crafting.getCpus()) if (cpu.getName() != null && cpu.getName().getString().equals(cpuName)) { selected = cpu; break; }
        final ICraftingCPU cpu = selected;
        ensureRequester();
        var source = target.source();
        var future = crafting.beginCraftingCalculation(target.level(), () -> source, key, count, CalculationStrategy.REPORT_MISSING_ITEMS);
        CraftingStatus status = new CraftingStatus();
        MinecraftServer server = target.level().getServer();
        IGridNode requestedThrough = target.gridNode();
        calculating++; ACTIVE.add(this);
        CompletableFuture.runAsync(() -> {
            try {
                ICraftingPlan plan = future.get();
                server.execute(() -> {
                    try {
                        if (!server.isRunning() || target == null || unavailable() != null || target.gridNode() != requestedThrough) { status.fail("AE network changed during calculation"); return; }
                        if (plan.simulation()) { status.fail("missing resources?"); return; }
                        var submitted = crafting.submitJob(plan, this, cpu, prioritize, source);
                        ICraftingLink link = submitted.link();
                        if (submitted.successful() && link != null) { links.add(link); status.attach(link); CraftingStatus.restore(link); }
                        else status.fail(submitted.errorCode() == null ? "missing resources?" : submitted.errorCode().toString());
                    } catch (RuntimeException error) { status.fail(error.toString()); OpenComputers.LOGGER.debug("Could not submit AE crafting plan", error); }
                    finally { calculating--; }
                });
            } catch (Exception error) {
                server.execute(() -> { calculating--; status.fail(error.toString()); });
                OpenComputers.LOGGER.debug("AE crafting calculation failed", error);
            }
        });
        return new Object[]{status};
    }

    private void ensureRequester() {
        IGridNode physical = target.gridNode();
        if (managed == null) {
            managed = GridHelper.createManagedNode(this, (owner, node) -> {
                if (owner.target != null && owner.target.level().hasChunkAt(owner.position)) owner.target.blockEntity().setChanged();
            }).setInWorldNode(false).setIdlePowerUsage(0).setTagName("requestNode")
                    .addService(ICraftingRequester.class, this);
            if (savedNode != null) { managed.loadFromNBT(savedNode); savedNode = null; }
            managed.setOwningPlayerId(physical.getOwningPlayerId());
            managed.create(target.level(), target.position());
        }
        if (connection == null || connection.getOtherSide(managed.getNode()) != physical) {
            if (connection != null) connection.destroy();
            connection = GridHelper.createConnection(managed.getNode(), physical);
        }
    }
    static void tick(MinecraftServer server) {
        for (Craftable value : Set.copyOf(ACTIVE)) {
            if (value.loading) {
                if (value.delay-- > 0) continue;
                try {
                    var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(value.dimension)));
                    if (level == null || !level.hasChunkAt(value.position)) { value.backoff(); continue; }
                    value.target = new AEUtil.Target(level, value.position, value.side);
                    value.ensureRequester(); value.loading = false;
                } catch (IllegalStateException error) { value.backoff(); continue; }
            }
            value.links.removeIf(link -> link.isCanceled() || link.isDone());
            if (value.links.isEmpty() && value.calculating == 0) {
                value.destroyRequester(); ACTIVE.remove(value);
            } else if (value.target != null) {
                try { value.ensureRequester(); }
                catch (IllegalStateException error) {
                    // Do not keep an active node attached to a removed/unloaded AE device.
                    value.destroyRequester(); value.target = null; value.loading = true; value.backoff();
                }
            }
        }
    }
    private void backoff() { delay = retry; retry = Math.min(100, retry * 2); }
    private void destroyRequester() {
        if (managed != null && !links.isEmpty()) { savedNode = new CompoundTag(); managed.saveToNBT(savedNode); }
        if (connection != null) { connection.destroy(); connection = null; }
        if (managed != null) { managed.destroy(); managed = null; }
    }
    static void stop() {
        for (Craftable value : Set.copyOf(ACTIVE)) value.destroyRequester();
        ACTIVE.clear(); CraftingStatus.clearCache();
    }

    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        this.registries = registries;
        key = AEItemKey.fromTag(registries, tag.getCompound("item"));
        if (key == null) throw new IllegalArgumentException("invalid persisted AE crafting item");
        outputAmount = Math.max(1, tag.getLong("amount")); dimension = tag.getString("dimension");
        position = BlockPos.of(tag.getLong("position")); side = tag.contains("side") ? Direction.from3DDataValue(tag.getInt("side")) : null;
        savedNode = tag.contains("requestNodeData") ? tag.getCompound("requestNodeData").copy() : null;
        links.clear();
        for (Tag entry : tag.getList("links", Tag.TAG_COMPOUND)) {
            ICraftingLink link = StorageHelper.loadCraftingLink((CompoundTag)entry, this);
            links.add(link); CraftingStatus.restore(link);
        }
        target = null; loading = true; retry = 1; delay = 1; ACTIVE.add(this);
    }
    @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
        this.registries = registries;
        tag.put("item", key.toTag(registries)); tag.putLong("amount", outputAmount); tag.putString("dimension", dimension);
        tag.putLong("position", position.asLong()); if (side != null) tag.putInt("side", side.get3DDataValue());
        ListTag jobs = new ListTag(); for (ICraftingLink link : links) { CompoundTag job = new CompoundTag(); link.writeToNBT(job); jobs.add(job); } tag.put("links", jobs);
        CompoundTag state = new CompoundTag();
        if (managed != null) managed.saveToNBT(state); else if (savedNode != null) state = savedNode.copy();
        tag.put("requestNodeData", state);
    }
    @Override public void load(CompoundTag tag) { if (registries == null) throw new IllegalStateException("AE Craftable requires host registry lookup"); load(tag, registries); }
    @Override public void save(CompoundTag tag) { if (registries == null) throw new IllegalStateException("AE Craftable requires host registry lookup"); save(tag, registries); }
}
