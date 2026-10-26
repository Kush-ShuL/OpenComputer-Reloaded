package li.cil.oc.server.component;

import java.util.LinkedHashMap;
import java.util.Map;
import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.common.redstone.RedstoneHost;
import net.minecraft.core.Direction;

/** Original advanced redstone callbacks, with sixteen channels on each host side. */
public final class RedstoneBundled extends Redstone {
    public RedstoneBundled(RedstoneHost host, boolean external) { super(host, external); }
    @Override public Map<String, String> getDeviceInfo() {
        return Map.of("class", "communication", "description", "Advanced redstone controller", "vendor", Constants.DeviceInfo.DefaultVendor,
                "product", "Rb800-M", "capacity", "65536", "width", "16");
    }
    @Callback(direct = true, doc = "function([side:number[, color:number]]):number or table -- Get bundled input, a side's colors, or all sides.")
    public Object[] getBundledInput(Context context, Arguments args) { return new Object[]{get(args, false)}; }
    @Callback(direct = true, doc = "function([side:number[, color:number]]):number or table -- Get bundled output, a side's colors, or all sides.")
    public Object[] getBundledOutput(Context context, Arguments args) { return new Object[]{get(args, true)}; }
    private Object get(Arguments args, boolean output) {
        var state = host.redstoneState();
        return switch (args.count()) {
            case 0 -> sides(output ? state.bundledOutputs() : state.bundledInputs());
            case 1 -> colors(output ? state.bundledOutput(side(args, 0)) : state.bundledInput(side(args, 0)));
            case 2 -> output ? state.bundledOutput(side(args, 0), color(args, 1)) : state.bundledInput(side(args, 0), color(args, 1));
            default -> throw new IllegalArgumentException("too many arguments, expected 0, 1, or 2");
        };
    }
    @Callback(doc = "function([side:number[, color:number,]] value:number or table):number or table -- Set bundled output and return previous values.")
    public Object[] setBundledOutput(Context context, Arguments args) {
        var state = host.redstoneState(); Object previous; boolean changed;
        switch (args.count()) {
            case 3 -> { Direction side = side(args, 0); int color = color(args, 1); previous = state.bundledOutput(side, color); changed = state.setBundledOutput(side, color, args.checkInteger(2)); }
            case 2 -> { Direction side = side(args, 0); previous = state.bundledOutput(side); changed = state.setBundledOutput(side, args.checkTable(1)); }
            case 1 -> { previous = state.bundledOutputs(); changed = state.setBundledOutput(args.checkTable(0)); }
            default -> throw new IllegalArgumentException("invalid number of arguments, expected 1, 2, or 3");
        }
        if (changed && Settings.get().redstoneDelay > 0) context.pause(Settings.get().redstoneDelay);
        return new Object[]{previous};
    }
    private static int color(Arguments args, int index) {
        int color = args.checkInteger(index);
        if (color < 0 || color > 15) throw new IllegalArgumentException("invalid color");
        return color;
    }
    private static Map<Integer, Integer> colors(int[] values) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int color = 0; color < Math.min(16, values.length); color++) result.put(color, values[color]);
        return result;
    }
    private static Map<Integer, Map<Integer, Integer>> sides(int[][] values) {
        Map<Integer, Map<Integer, Integer>> result = new LinkedHashMap<>();
        for (int side = 0; side < Math.min(6, values.length); side++) result.put(side, colors(values[side]));
        return result;
    }
}
