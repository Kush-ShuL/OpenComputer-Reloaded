package li.cil.oc.api;

import com.typesafe.config.Config;
import li.cil.oc.api.detail.DriverAPI;
import li.cil.oc.api.detail.FileSystemAPI;
import li.cil.oc.api.detail.ItemAPI;
import li.cil.oc.api.detail.MachineAPI;
import li.cil.oc.api.detail.ManualAPI;
import li.cil.oc.api.detail.NanomachinesAPI;
import li.cil.oc.api.detail.NetworkAPI;

/**
 * Central reference for the API.
 * <br>
 * Don't use this class directly, prefer using the other classes in this
 * package instead. This class is initialized by OpenComputers in the
 * common setup phase, so integrations should register through enqueued common setup work.
 */
public class API {
    public static final String ID_OWNER = "opencomputers";
    public static final String VERSION = "8.0.0-alpha.1";

    // ----------------------------------------------------------------------- //

    /**
     * The loaded config.
     */
    public static Config config = null;

    /**
     * Whether OpenComputers uses power.
     * <br>
     * This is set in the init phase, so do not rely it before the post-init phase.
     */
    public static boolean isPowerEnabled = false;

    // ----------------------------------------------------------------------- //
    // Prefer using the static methods in the respective classes in this package
    // over accessing these instances directly.

    public static DriverAPI driver = null;
    public static FileSystemAPI fileSystem = null;
    public static ItemAPI items = null;
    public static MachineAPI machine = null;
    public static ManualAPI manual = null;
    public static NanomachinesAPI nanomachines = null;
    public static NetworkAPI network = null;

    // ----------------------------------------------------------------------- //

    private API() {
    }
}
