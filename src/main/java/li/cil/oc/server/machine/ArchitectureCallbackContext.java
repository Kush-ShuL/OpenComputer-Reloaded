package li.cil.oc.server.machine;

import java.util.concurrent.Callable;

/** Identifies an architecture callback running on its VM's separate Java coroutine thread. */
public final class ArchitectureCallbackContext {
    private static final ThreadLocal<li.cil.oc.api.machine.Machine> CURRENT = new ThreadLocal<>();

    private ArchitectureCallbackContext() {}

    public static <T> T call(li.cil.oc.api.machine.Machine machine, Callable<T> callback) throws Exception {
        li.cil.oc.api.machine.Machine previous = CURRENT.get();
        CURRENT.set(machine);
        try { return callback.call(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    static boolean isCallbackFor(li.cil.oc.api.machine.Machine machine) { return CURRENT.get() == machine; }
}
