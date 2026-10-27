package li.cil.oc.common.integration.projectred;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import li.cil.oc.common.redstone.BundledRedstone;
import li.cil.oc.common.redstone.RedstoneHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** The original unsigned byte input and enabled-host output over the existing OC state. */
final class ProjectRedRedstone implements BundledRedstone.Provider, InvocationHandler {
    private final ProjectRedAccess access;

    ProjectRedRedstone(ProjectRedAccess access) { this.access = access; }

    Object interaction() {
        return Proxy.newProxyInstance(access.interactionType.getClassLoader(), new Class<?>[]{access.interactionType}, this);
    }

    @Override public int computeInput(Level level, BlockPos position, Direction side) { return 0; }

    @Override public int[] computeBundledInput(Level level, BlockPos position, Direction side) {
        if (!ProjectRedIntegration.available() || level == null || level.isClientSide || side == null
            || !level.hasChunkAt(position) || !level.hasChunkAt(position.relative(side))) return null;
        byte[] input = access.input(level, position, side);
        if (input == null) return null;
        int[] values = new int[input.length];
        for (int color = 0; color < input.length; color++) values[color] = input[color] & 0xFF;
        return values;
    }

    private static RedstoneHost host(Level level, BlockPos position, Direction side) {
        if (!ProjectRedIntegration.available() || level == null || position == null || side == null || !level.hasChunkAt(position)) return null;
        return level.getBlockEntity(position) instanceof RedstoneHost host ? host : null;
    }

    @Override public Object invoke(Object proxy, Method method, Object[] arguments) {
        if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "OpenComputers ProjectRed bundled output bridge";
            default -> throw new UnsupportedOperationException(method.toString());
        };
        RedstoneHost host = host((Level) arguments[0], (BlockPos) arguments[1], (Direction) arguments[2]);
        return switch (method.getName()) {
            case "isValidInteractionFor" -> host != null;
            case "canConnectBundled" -> host != null && host.redstoneState().enabled();
            case "getBundledSignal" -> {
                if (host == null) yield null;
                byte[] result = new byte[16];
                if (host.redstoneState().enabled()) {
                    int[] output = host.redstoneState().bundledOutput((Direction) arguments[2]);
                    for (int color = 0; color < result.length; color++) result[color] = (byte) Math.clamp(output[color], 0, 255);
                }
                yield result;
            }
            default -> throw new UnsupportedOperationException("Unsupported ProjectRed interaction method: " + method);
        };
    }
}
