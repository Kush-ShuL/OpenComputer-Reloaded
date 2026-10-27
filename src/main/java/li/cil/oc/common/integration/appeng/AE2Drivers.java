package li.cil.oc.common.integration.appeng;

import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.automation.ExportBusPart;
import appeng.parts.automation.ImportBusPart;
import appeng.parts.misc.InterfacePart;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Five original driver surfaces, backed by real AE block/part instances. */
final class AE2Drivers implements DriverBlock, EnvironmentProvider {
    enum Kind {
        CONTROLLER(ControllerBlockEntity.class, "me_controller", 5),
        BLOCK_INTERFACE(InterfaceBlockEntity.class, "me_interface", 5),
        PART_INTERFACE(InterfacePart.class, "me_interface", 0),
        EXPORT(ExportBusPart.class, "me_exportbus", 2),
        IMPORT(ImportBusPart.class, "me_importbus", 1);
        final Class<?> type; final String name; final int priority;
        Kind(Class<?> type, String name, int priority) { this.type = type; this.name = name; this.priority = priority; }
    }
    private final Kind kind;
    AE2Drivers(Kind kind) { this.kind = kind; }
    @Override public boolean worksWith(Level level, BlockPos pos, Direction side) {
        if (!AE2Integration.available() || !level.hasChunkAt(pos)) return false;
        var block = level.getBlockEntity(pos);
        if (kind.type.isInstance(block)) return true;
        if (block instanceof IPartHost host) for (Direction face : Direction.values()) if (kind.type.isInstance(host.getPart(face))) return true;
        return false;
    }
    @Override public ManagedEnvironment createEnvironment(Level level, BlockPos pos, Direction side) {
        if (!(level instanceof ServerLevel server) || !worksWith(level, pos, side)) return null;
        return switch (kind) {
            case CONTROLLER -> new Controller(new AEUtil.Target(server, pos, null));
            case BLOCK_INTERFACE -> new BlockInterface(new AEUtil.Target(server, pos, null));
            case PART_INTERFACE -> new PartInterface(new AEUtil.Target(server, pos, side == null ? firstPart(level, pos) : side.getOpposite()));
            case EXPORT -> new ExportBus(server, pos);
            case IMPORT -> new ImportBus(server, pos);
        };
    }
    private Direction firstPart(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof IPartHost host) for (Direction side : Direction.values()) if (kind.type.isInstance(host.getPart(side))) return side;
        return Direction.DOWN;
    }
    @Override public Class<?> getEnvironment(ItemStack stack) {
        return switch (kind) {
            case CONTROLLER -> AEBlocks.CONTROLLER.is(stack) ? Controller.class : null;
            case BLOCK_INTERFACE -> AEBlocks.INTERFACE.is(stack) ? BlockInterface.class : null;
            case PART_INTERFACE -> AEParts.INTERFACE.is(stack) ? PartInterface.class : null;
            case EXPORT -> AEParts.EXPORT_BUS.is(stack) ? ExportBus.class : null;
            case IMPORT -> AEParts.IMPORT_BUS.is(stack) ? ImportBus.class : null;
        };
    }

    static abstract class Environment extends AbstractManagedEnvironment implements NamedBlock {
        final Kind kind;
        Environment(Kind kind) { this.kind = kind; setNode(Network.newNode(this, Visibility.Network).withComponent(kind.name).create()); }
        @Override public String preferredName() { return kind.name; }
        @Override public int priority() { return kind.priority; }
        Object[] getConfiguration(AEUtil.Target target, Arguments args, int offset) {
            var config = target.config();
            int slot = AEUtil.slot(args, offset, config.size(), 0);
            return new Object[]{AEUtil.configStack(config.getStack(slot))};
        }
        Object[] setConfiguration(AEUtil.Target target, Context context, Arguments args, int offset) {
            var config = target.config();
            boolean shortForm = args.isString(offset);
            int slot = shortForm ? 0 : AEUtil.slot(args, offset, config.size(), 0);
            int addressIndex = offset + (shortForm ? 0 : 1);
            GenericStack value = null;
            if (args.count() > addressIndex) {
                var database = AEUtil.database(node(), args.checkString(addressIndex));
                int entry = AEUtil.slot(args, addressIndex + 1, database.size(), 0);
                int count = args.optInteger(addressIndex + 2, 1);
                ItemStack stack = database.getStackInSlot(entry);
                if (stack != null && !stack.isEmpty() && count > 0) value = new GenericStack(AEItemKey.of(stack), Math.min(count, stack.getMaxStackSize()));
            }
            config.setStack(slot, value);
            target.blockEntity().setChanged();
            context.pause(.5);
            return new Object[]{true};
        }
    }

    public static final class Controller extends NetworkControl {
        Controller(AEUtil.Target target) { super(Kind.CONTROLLER, target); }
    }
    public static final class BlockInterface extends NetworkControl {
        BlockInterface(AEUtil.Target target) { super(Kind.BLOCK_INTERFACE, target); }
        @Callback(doc="function([slot:number]):table -- Get the configuration of the interface.")
        public Object[] getInterfaceConfiguration(Context context, Arguments args) { return getConfiguration(target, args, 0); }
        @Callback(doc="function([slot:number][, database:address, entry:number[, size:number]]):boolean -- Configure the interface.")
        public Object[] setInterfaceConfiguration(Context context, Arguments args) { return setConfiguration(target, context, args, 0); }
    }
    public static final class PartInterface extends NetworkControl {
        PartInterface(AEUtil.Target target) { super(Kind.PART_INTERFACE, target); }
        private AEUtil.Target selected(Arguments args) { return part(target.level(), target.position(), AEUtil.side(args, 0), InterfacePart.class); }
        @Callback(doc="function(side:number[, slot:number]):table -- Get the configuration of the interface pointing in the specified direction.")
        public Object[] getInterfaceConfiguration(Context context, Arguments args) { var selected = selected(args); return selected == null ? new Object[]{null, "no matching part"} : getConfiguration(selected, args, 1); }
        @Callback(doc="function(side:number[, slot:number][, database:address, entry:number[, size:number]]):boolean -- Configure the interface pointing in the specified direction.")
        public Object[] setInterfaceConfiguration(Context context, Arguments args) { var selected = selected(args); return selected == null ? new Object[]{null, "no matching part"} : setConfiguration(selected, context, args, 1); }
    }
    static AEUtil.Target part(ServerLevel level, BlockPos position, Direction side, Class<?> type) {
        if (!level.hasChunkAt(position) || !(level.getBlockEntity(position) instanceof IPartHost host) || !type.isInstance(host.getPart(side))) return null;
        return new AEUtil.Target(level, position, side);
    }
    static abstract class Bus extends Environment {
        final ServerLevel level; final BlockPos position;
        Bus(Kind kind, ServerLevel level, BlockPos position) { super(kind); this.level = level; this.position = position.immutable(); }
        AEUtil.Target selected(Arguments args) { return part(level, position, AEUtil.side(args, 0), kind.type); }
        Object[] get(Context context, Arguments args) { var selected = selected(args); return selected == null ? new Object[]{null, "no matching part"} : getConfiguration(selected, args, 1); }
        Object[] set(Context context, Arguments args) { var selected = selected(args); return selected == null ? new Object[]{null, "no matching part"} : setConfiguration(selected, context, args, 1); }
    }
    public static final class ImportBus extends Bus {
        ImportBus(ServerLevel level, BlockPos position) { super(Kind.IMPORT, level, position); }
        @Callback(doc="function(side:number[, slot:number]):table -- Get the configuration of the import bus pointing in the specified direction.")
        public Object[] getImportConfiguration(Context context, Arguments args) { return get(context, args); }
        @Callback(doc="function(side:number[, slot:number][, database:address, entry:number]):boolean -- Configure the import bus pointing in the specified direction.")
        public Object[] setImportConfiguration(Context context, Arguments args) { return set(context, args); }
    }
    public static final class ExportBus extends Bus {
        ExportBus(ServerLevel level, BlockPos position) { super(Kind.EXPORT, level, position); }
        @Callback(doc="function(side:number[, slot:number]):table -- Get the configuration of the export bus pointing in the specified direction.")
        public Object[] getExportConfiguration(Context context, Arguments args) { return get(context, args); }
        @Callback(doc="function(side:number[, slot:number][, database:address, entry:number]):boolean -- Configure the export bus pointing in the specified direction.")
        public Object[] setExportConfiguration(Context context, Arguments args) { return set(context, args); }
        @Callback(doc="function(side:number[, slot:number]):number -- Make the export bus facing the specified direction perform one export operation into the specified slot.")
        public Object[] exportIntoSlot(Context context, Arguments args) {
            var target = selected(args);
            if (target == null) return new Object[]{null, "no export bus"};
            var bus = (ExportBusPart) target.device();
            if (!bus.getMainNode().isActive()) return new Object[]{null, "no ae grid"};
            Direction direction = target.partSide();
            BlockPos destination = position.relative(direction);
            if (!level.hasChunkAt(destination)) return new Object[]{null, "no inventory"};
            IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, destination, direction.getOpposite());
            if (inventory == null) return new Object[]{null, "no inventory"};
            int slot = args.count() > 1 ? AEUtil.slot(args, 1, inventory.getSlots(), 0) : -1;
            int speed = bus.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD);
            int limit = switch (speed) { case 1 -> 8; case 2 -> 32; case 3 -> 64; case 4 -> 96; default -> 1; };
            int remaining = limit;
            var grid = target.grid(); var storage = grid.getStorageService().getInventory(); var source = target.source();
            var available = grid.getStorageService().getCachedInventory();
            boolean fuzzy = bus.getUpgrades().isInstalled(AEItems.FUZZY_CARD);
            var mode = bus.getConfigManager().getSetting(appeng.api.config.Settings.FUZZY_MODE);
            int configSlots = Math.min(bus.getConfig().size(), 18 + 9 * bus.getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD));
            for (int configSlot = 0; configSlot < configSlots && remaining > 0; configSlot++) {
                var key = bus.getConfig().getKey(configSlot);
                if (!(key instanceof AEItemKey)) continue;
                var candidates = fuzzy ? available.findFuzzy(key, mode).stream().map(entry -> entry.getKey()).toList() : List.of(key);
                for (var candidate : candidates) {
                    if (!(candidate instanceof AEItemKey item) || remaining <= 0) continue;
                    int wanted = Math.min(remaining, item.getMaxStackSize());
                    ItemStack sample = item.toStack(wanted);
                    ItemStack rejected = insert(inventory, slot, sample, true);
                    int accepted = wanted - rejected.getCount();
                    if (accepted <= 0) continue;
                    long extracted = StorageHelper.poweredExtraction(grid.getEnergyService(), storage, item, accepted, source);
                    if (extracted <= 0) continue;
                    ItemStack leftover = insert(inventory, slot, item.toStack((int)extracted), false);
                    int moved = (int)extracted - leftover.getCount();
                    if (!leftover.isEmpty()) {
                        long restored = storage.insert(item, leftover.getCount(), Actionable.MODULATE, source);
                        leftover.shrink((int)restored);
                        if (!leftover.isEmpty()) Containers.dropItemStack(level, destination.getX() + .5, destination.getY() + .5, destination.getZ() + .5, leftover);
                    }
                    if (moved > 0) { remaining -= moved; context.pause(.25); }
                }
            }
            return remaining == limit ? new Object[]{null, "no items moved"} : new Object[]{limit - remaining};
        }
        private ItemStack insert(IItemHandler inventory, int slot, ItemStack stack, boolean simulate) {
            return slot < 0 ? ItemHandlerHelper.insertItemStacked(inventory, stack, simulate) : inventory.insertItem(slot, stack, simulate);
        }
    }
}
