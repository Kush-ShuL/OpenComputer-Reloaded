package li.cil.oc.util;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.util.thread.EffectiveSide;

/** Logical-side queries shared by server code and asynchronous computer workers. */
public final class SideTracker {
    private static final ThreadLocal<Boolean> computerWorker = ThreadLocal.withInitial(() -> false);

    private SideTracker() {}

    public static boolean isServer() {
        return computerWorker.get() || EffectiveSide.get().isServer();
    }

    public static boolean isClient() {
        return !isServer();
    }

    /** Wrap computer workers, whose thread group is not the game's server group. */
    public static Runnable serverTask(Runnable task) {
        return () -> {
            boolean previous = computerWorker.get();
            computerWorker.set(true);
            try { task.run(); }
            finally {
                if (previous) computerWorker.set(true);
                else computerWorker.remove();
            }
        };
    }

    public static boolean isGamePaused() {
        return FMLEnvironment.dist == Dist.CLIENT && ClientState.isPaused();
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientState {
        private static boolean isPaused() {
            return net.minecraft.client.Minecraft.getInstance().isPaused();
        }
    }
}
