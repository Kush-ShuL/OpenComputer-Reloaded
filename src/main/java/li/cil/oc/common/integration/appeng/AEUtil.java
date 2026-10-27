package li.cil.oc.common.integration.appeng;

import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.parts.misc.InterfacePart;
import appeng.parts.automation.IOBusPart;
import appeng.util.ConfigInventory;
import li.cil.oc.api.internal.Database;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.network.Node;
import li.cil.oc.server.driver.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

final class AEUtil {
    private AEUtil() {}
    record Target(ServerLevel level, BlockPos position, Direction partSide) {
        Target { position = position.immutable(); }
        BlockEntity blockEntity() {
            if (!level.hasChunkAt(position)) throw new IllegalStateException("AE block is not loaded");
            BlockEntity tile = level.getBlockEntity(position);
            if (tile == null || tile.isRemoved()) throw new IllegalStateException("no controller");
            return tile;
        }
        Object device() {
            BlockEntity tile = blockEntity();
            if (partSide == null) return tile;
            if (!(tile instanceof IPartHost host) || host.getPart(partSide) == null) throw new IllegalStateException("no matching part");
            return host.getPart(partSide);
        }
        IActionHost actionHost() {
            if (device() instanceof IActionHost host) return host;
            throw new IllegalStateException("no ae grid");
        }
        IGridNode gridNode() {
            IGridNode node = actionHost().getActionableNode();
            if (node == null) throw new IllegalStateException("no ae grid");
            return node;
        }
        IGrid grid() { return gridNode().getGrid(); }
        MachineSource source() { return new MachineSource(actionHost()); }
        ConfigInventory config() {
            Object device = device();
            if (device instanceof InterfaceBlockEntity block) return block.getInterfaceLogic().getConfig();
            if (device instanceof InterfacePart part) return part.getInterfaceLogic().getConfig();
            if (device instanceof IOBusPart bus) return bus.getConfig();
            throw new IllegalStateException("no matching part");
        }
    }

    static Direction side(Arguments args, int index) {
        int value = args.checkInteger(index);
        if (value < 0 || value > 5) throw new IllegalArgumentException("invalid side");
        return Direction.from3DDataValue(value);
    }
    static int slot(Arguments args, int index, int size, int fallback) {
        int value = args.optInteger(index, fallback + 1) - 1;
        if (value < 0 || value >= size) throw new IllegalArgumentException("invalid slot");
        return value;
    }
    static Database database(Node own, String address) {
        if (own == null || own.network() == null) throw new IllegalArgumentException("no such component");
        Node found = own.network().node(address);
        if (!(found instanceof li.cil.oc.api.network.Component component) || !component.canBeSeenFrom(own)) throw new IllegalArgumentException("no such component");
        if (!(found.host() instanceof Database database)) throw new IllegalArgumentException("not a database");
        return database;
    }
    static Database firstDatabase(Node own) {
        if (own != null && own.network() != null) for (Node node : own.network().nodes(own)) {
            if (node instanceof li.cil.oc.api.network.Component component && component.canBeSeenFrom(own) && node.host() instanceof Database database) return database;
        }
        throw new IllegalArgumentException("no database upgrade found");
    }
    @SuppressWarnings("unchecked")
    static Map<Object, Object> item(AEItemKey key, long amount, boolean craftable, HolderLookup.Provider registries) {
        var result = new LinkedHashMap<>((Map<Object, Object>) Registry.convert(new Object[]{key.toStack((int)Math.min(Integer.MAX_VALUE, Math.max(1, amount)))}, registries)[0]);
        result.put("size", amount); result.put("isCraftable", craftable);
        return result;
    }
    static ItemStack configStack(GenericStack stack) {
        // AE's CONFIG_TYPES inventories retain a valid filter key with amount 0.
        // Like its public ConfigMenuInventory wrapper, represent that key by one
        // item; positive CONFIG_STACKS quantities still retain their real count.
        return stack != null && stack.what() instanceof AEItemKey item
                ? item.toStack((int)Math.min(Integer.MAX_VALUE, Math.max(1, stack.amount()))) : ItemStack.EMPTY;
    }
    static Map<?, ?> filter(Arguments args, int index) { return args.optTable(index, Map.of()); }
    static boolean matches(Object actual, Object filter) {
        if (filter instanceof Map<?, ?> map) {
            if (actual != null && actual.getClass().isArray() && map.keySet().stream().allMatch(key -> key instanceof Number || Objects.equals(key, "n"))) {
                for (var entry : map.entrySet()) if (entry.getKey() instanceof Number) {
                    boolean found = false;
                    for (int i = 0; i < Array.getLength(actual); i++) if (matches(Array.get(actual, i), entry.getValue())) { found = true; break; }
                    if (!found) return false;
                }
                return true;
            }
            if (!(actual instanceof Map<?, ?> values)) return false;
            for (var entry : map.entrySet()) {
                if (Objects.equals(entry.getKey(), "n")) continue;
                Object key = entry.getKey() instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : entry.getKey();
                if (!values.containsKey(key) || !matches(values.get(key), entry.getValue())) return false;
            }
            return true;
        }
        if (filter instanceof Number number && actual instanceof Number other) return Double.compare(number.doubleValue(), other.doubleValue()) == 0;
        if (filter != null && actual != null && filter.getClass().isArray() && actual.getClass().isArray()) {
            if (Array.getLength(filter) != Array.getLength(actual)) return false;
            for (int i = 0; i < Array.getLength(filter); i++) if (!matches(Array.get(actual, i), Array.get(filter, i))) return false;
            return true;
        }
        if (filter instanceof byte[] bytes && actual instanceof String text) return text.equals(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        return Objects.equals(actual, filter);
    }
}
