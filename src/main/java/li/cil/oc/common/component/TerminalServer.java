package li.cil.oc.common.component;

import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Items;
import li.cil.oc.api.Network;
import li.cil.oc.api.component.RackBusConnectable;
import li.cil.oc.api.component.RackMountable;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.internal.Keyboard;
import li.cil.oc.api.internal.Rack;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.util.Lifecycle;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Original rack terminal pairing, private input network and delayed-address cache. */
public class TerminalServer implements EnvironmentHost, Analyzable, RackMountable, Lifecycle, DeviceInfo {
    public static final TerminalServerCache loaded = new TerminalServerCache();
    public final Rack rack;
    public final int slot;
    private final Node node;
    private li.cil.oc.api.internal.TextBuffer buffer;
    private Keyboard keyboard;
    public double range = Settings.get().maxWirelessRange[1];
    public final List<String> keys = new ArrayList<>();

    public TerminalServer(Rack rack, int slot) {
        this.rack = Objects.requireNonNull(rack);
        this.slot = slot;
        node = Network.newNode(this, Visibility.None).create();
    }
    @Override public Node node() { return node; }
    public synchronized li.cil.oc.api.internal.TextBuffer buffer() {
        if (buffer == null) {
            ItemStack screen = Objects.requireNonNull(Items.get(Constants.BlockName.ScreenTier1)).createItemStack(1);
            buffer = (li.cil.oc.api.internal.TextBuffer) Objects.requireNonNull(Driver.driverFor(screen, getClass())).createEnvironment(screen, this);
            buffer.setMaximumResolution(Settings.screenResolutionsByTier[2][0], Settings.screenResolutionsByTier[2][1]);
            buffer.setMaximumColorDepth(Settings.screenDepthsByTier[2]);
        }
        return buffer;
    }
    public synchronized Keyboard keyboard() {
        if (keyboard == null) {
            ItemStack item = Objects.requireNonNull(Items.get(Constants.BlockName.Keyboard)).createItemStack(1);
            keyboard = (Keyboard) Objects.requireNonNull(Driver.driverFor(item, getClass())).createEnvironment(item, this);
            keyboard.setUsableOverride((ignored, player) -> {
                ItemStack held = player.getMainHandItem();
                return Items.get(held) != null && Items.get(held) == Items.get(Constants.ItemName.Terminal) &&
                        sidedKeys().contains(held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("oc:key"));
            });
        }
        return keyboard;
    }
    public boolean hasAddress() {
        CompoundTag data = rack.getMountableData(slot);
        return data != null && data.contains("terminalAddress");
    }
    public String address() { CompoundTag data = rack.getMountableData(slot); return data == null ? "" : data.getString("terminalAddress"); }
    public List<String> sidedKeys() {
        if (!world().isClientSide) return List.copyOf(keys);
        CompoundTag data = rack.getMountableData(slot);
        if (data == null) return List.of();
        ListTag list = data.getList("keys", Tag.TAG_STRING);
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) result.add(list.getString(i));
        return result;
    }
    @Override public Map<String, String> getDeviceInfo() {
        return Map.of(DeviceAttribute.Class, DeviceClass.Generic, DeviceAttribute.Description, "Terminal server",
                DeviceAttribute.Vendor, Constants.DeviceInfo.DefaultVendor, DeviceAttribute.Product, "RemoteViewing EX");
    }
    @Override public void onConnect(Node connected) {
        if (connected == node) { node.connect(buffer().node()); node.connect(keyboard().node()); buffer().node().connect(keyboard().node()); }
    }
    @Override public void onDisconnect(Node disconnected) {
        if (disconnected == node) { if (buffer != null && buffer.node() != null) buffer.node().remove(); if (keyboard != null && keyboard.node() != null) keyboard.node().remove(); }
    }
    @Override public void onMessage(Message message) {}
    @Override public Level world() { return rack.world(); }
    @Override public double xPosition() { return rack.xPosition(); }
    @Override public double yPosition() { return rack.yPosition(); }
    @Override public double zPosition() { return rack.zPosition(); }
    @Override public void markChanged() { rack.markChanged(); }
    @Override public CompoundTag getData() {
        if (node.address() == null) Network.joinNewNetwork(node);
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag(); for (String key : keys) list.add(StringTag.valueOf(key));
        tag.put("keys", list); tag.putString("terminalAddress", node.address()); return tag;
    }
    @Override public int getConnectableCount() { return 0; }
    @Override public RackBusConnectable getConnectableAt(int index) { return null; }
    @Override public boolean onActivate(Player player, InteractionHand hand, ItemStack held, float hitX, float hitY) {
        if (Items.get(held) == null || Items.get(held) != Items.get(Constants.ItemName.Terminal)) return false;
        if (!world().isClientSide) {
            CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            keys.remove(tag.getString("oc:key"));
            int limit = Math.max(1, Settings.get().terminalsPerServer);
            while (keys.size() >= limit) keys.removeFirst();
            String key = UUID.randomUUID().toString(); keys.add(key);
            tag.putString("oc:key", key); tag.putString("oc:server", node.address());
            held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            rack.markChanged(slot); player.getInventory().setChanged();
        }
        return true;
    }
    @Override public void load(CompoundTag tag) { load(tag, world().registryAccess()); }
    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!world().isClientSide) node.load(tag, registries);
        buffer().load(tag.getCompound("oc:buffer"), registries);
        keyboard().load(tag.getCompound("oc:keyboard"), registries);
        keys.clear(); ListTag list = tag.getList("oc:keys", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) keys.add(list.getString(i));
    }
    @Override public void save(CompoundTag tag) { save(tag, world().registryAccess()); }
    @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
        node.save(tag, registries);
        CompoundTag screenTag = new CompoundTag(), keyboardTag = new CompoundTag();
        buffer().save(screenTag, registries); keyboard().save(keyboardTag, registries);
        tag.put("oc:buffer", screenTag); tag.put("oc:keyboard", keyboardTag);
        ListTag list = new ListTag(); for (String key : keys) list.add(StringTag.valueOf(key)); tag.put("oc:keys", list);
    }
    @Override public boolean canUpdate() { return true; }
    @Override public void update() { if (world().isClientSide || node.address() != null && node.network() != null) buffer().update(); }
    @Override public EnumSet<State> getCurrentState() { return EnumSet.noneOf(State.class); }
    @Override public Node[] onAnalyze(Player player, Direction side, float hitX, float hitY, float hitZ) { return new Node[]{buffer().node(), keyboard().node()}; }
    @Override public void onLifecycleStateChange(LifecycleState state) {
        if (world().isClientSide) { if (state == LifecycleState.Initialized) loaded.add(this); else if (state == LifecycleState.Disposed) loaded.remove(this); }
    }

    public static final class TerminalServerCache {
        private final Map<String, TerminalServer> ready = new LinkedHashMap<>();
        private final LinkedHashSet<TerminalServer> pending = new LinkedHashSet<>();
        private void completePending() {
            for (TerminalServer terminal : pending.toArray(TerminalServer[]::new)) if (terminal.hasAddress()) {
                pending.remove(terminal); ready.putIfAbsent(terminal.address(), terminal);
            }
        }
        public synchronized boolean add(TerminalServer terminal) {
            completePending();
            return terminal.hasAddress() ? ready.putIfAbsent(terminal.address(), terminal) == null : pending.add(terminal);
        }
        public synchronized boolean remove(TerminalServer terminal) {
            completePending();
            return terminal.hasAddress() ? ready.remove(terminal.address(), terminal) : pending.remove(terminal);
        }
        public synchronized void clear() { ready.clear(); pending.clear(); }
        public synchronized Optional<TerminalServer> find(String address) { completePending(); return Optional.ofNullable(ready.get(address)); }
    }
}
