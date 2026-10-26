package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.network.*;

/** The battery is a real connector buffer and deliberately has no Lua component. */
public final class UpgradeBattery extends UpgradeBase {
    public UpgradeBattery(EnvironmentHost host,int tier){super(host,null,"Battery","Unlimited Power (Almost Ed.)",false);setNode(li.cil.oc.api.Network.newNode(this,Visibility.Network).withConnector(Settings.get().bufferCapacitorUpgrades[tier]).create());}
}
