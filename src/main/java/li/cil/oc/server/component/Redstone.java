package li.cil.oc.server.component;

import java.util.LinkedHashMap;
import java.util.Map;
import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.common.redstone.RedstoneChange;
import li.cil.oc.common.redstone.RedstoneHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/** Vanilla redstone controller and original threshold-driven wakeup signals. */
public class Redstone extends AbstractManagedEnvironment implements DeviceInfo {
    protected final RedstoneHost host;
    private final boolean wakeNeighborsOnly;
    private volatile int wakeThreshold;

    public Redstone(RedstoneHost host, boolean external) {
        this.host = host; wakeNeighborsOnly = !external;
        setNode(Network.newNode(this, Visibility.Network).withComponent("redstone", external ? Visibility.Network : Visibility.Neighbors).create());
    }
    @Override public Map<String, String> getDeviceInfo() {
        return Map.of("class", "communication", "description", "Redstone controller", "vendor", Constants.DeviceInfo.DefaultVendor,
                "product", "Rs100-V", "capacity", "16", "width", "1");
    }
    @Callback(direct = true, doc = "function([side:number]):number or table -- Get the redstone input.")
    public Object[] getInput(Context context, Arguments args) {
        return new Object[]{args.count() == 1 ? host.redstoneState().input(side(args, 0)) : values(host.redstoneState().inputs())};
    }
    @Callback(direct = true, doc = "function([side:number]):number or table -- Get the redstone output.")
    public Object[] getOutput(Context context, Arguments args) {
        return new Object[]{args.count() == 1 ? host.redstoneState().output(side(args, 0)) : values(host.redstoneState().outputs())};
    }
    @Callback(doc = "function([side:number, ]value:number or table):number or table -- Set output and return the previous value.")
    public Object[] setOutput(Context context, Arguments args) throws Exception {
        Object old; boolean changed;
        if (args.count() == 2) {
            Direction side = side(args, 0); old = host.redstoneState().output(side);
            changed = host.redstoneState().setOutput(side, args.checkInteger(1));
        } else if (args.count() == 1) {
            old = values(host.redstoneState().outputs()); changed = host.redstoneState().setOutput(args.checkTable(0));
        } else throw new Exception("invalid number of arguments, expected 1 or 2");
        if (changed && Settings.get().redstoneDelay > 0) context.pause(Settings.get().redstoneDelay);
        return new Object[]{old};
    }
    @Callback(direct = true, doc = "function(side:number):number -- Get the comparator input on the specified side.")
    public Object[] getComparatorInput(Context context, Arguments args) {
        Direction side = side(args, 0);
        BlockPos pos = BlockPos.containing(host.xPosition(), host.yPosition(), host.zPosition()).relative(side);
        var level = host.world();
        if (level != null && level.hasChunkAt(pos)) {
            var state = level.getBlockState(pos);
            if (state.hasAnalogOutputSignal()) return new Object[]{state.getAnalogOutputSignal(level, pos)};
        }
        return new Object[]{0};
    }
    @Callback(direct = true, doc = "function():number -- Get the current wake-up threshold.")
    public Object[] getWakeThreshold(Context context, Arguments args) { return new Object[]{wakeThreshold}; }
    @Callback(doc = "function(threshold:number):number -- Set the wake-up threshold.")
    public Object[] setWakeThreshold(Context context, Arguments args) {
        int old = wakeThreshold; wakeThreshold = args.checkInteger(0); host.markChanged(); return new Object[]{old};
    }
    public void redstoneChanged(RedstoneChange change) {
        if (node().network() == null) return;
        Object side = change.side() == null ? "wireless" : change.side().get3DDataValue();
        Object[] signal = change.color() < 0 ? new Object[]{"redstone_changed", side, change.oldValue(), change.newValue()}
                : new Object[]{"redstone_changed", side, change.oldValue(), change.newValue(), change.color()};
        node().sendToReachable("computer.signal", signal);
        if (change.oldValue() < wakeThreshold && change.newValue() >= wakeThreshold) {
            if (wakeNeighborsOnly) node().sendToNeighbors("computer.start");
            else node().sendToReachable("computer.start");
        }
    }
    @Override public void onMessage(Message message) {
        if (message.name().equals("redstone.changed") && message.data().length == 1 && message.data()[0] instanceof RedstoneChange change)
            redstoneChanged(change);
    }
    protected Direction side(Arguments args, int index) {
        int side = args.checkInteger(index);
        if (side < 0 || side > 5) throw new IllegalArgumentException("invalid side");
        return host.toGlobal(Direction.from3DDataValue(side));
    }
    private static Map<Integer, Integer> values(int[] input) {
        Map<Integer, Integer> values = new LinkedHashMap<>();
        for (int i = 0; i < input.length; i++) values.put(i, input[i]);
        return values;
    }
    @Override public void load(CompoundTag tag) { super.load(tag); wakeThreshold = tag.getInt("wakeThreshold"); }
    @Override public void save(CompoundTag tag) { super.save(tag); tag.putInt("wakeThreshold", wakeThreshold); }
}
