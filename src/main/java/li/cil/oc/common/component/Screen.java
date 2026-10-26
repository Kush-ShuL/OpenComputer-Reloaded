package li.cil.oc.common.component;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.EnvironmentHost;

public class Screen extends TextBuffer {
    public interface Owner extends EnvironmentHost {
        boolean invertTouchMode();
        void setInvertTouchMode(boolean value);
        void synchronizeTouchMode(boolean value);
    }
    public final Owner screen;
    public Screen(Owner screen, HostServices services) { super(screen, services); this.screen = screen; }
    @Callback(direct = true, doc = "function():boolean -- Returns whether sneak-activate opens the screen GUI.")
    public Object[] isTouchModeInverted(Context context, Arguments args) { return new Object[]{screen.invertTouchMode()}; }
    @Callback(doc = "function(value:boolean):boolean -- Invert touch mode; returns the previous setting.")
    public Object[] setTouchModeInverted(Context context, Arguments args) {
        boolean previous = screen.invertTouchMode(), value = args.checkBoolean(0);
        if (previous != value) { screen.setInvertTouchMode(value); screen.synchronizeTouchMode(value); }
        return new Object[]{previous};
    }
}
