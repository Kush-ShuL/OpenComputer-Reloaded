package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.network.*;
import java.util.Map;

/** The battery is a real connector buffer and deliberately has no Lua component. */
public final class UpgradeBattery extends UpgradeBase {
    public final int tier;
    public UpgradeBattery(EnvironmentHost host,int tier){super(host,null,"Battery","Unlimited Power (Almost Ed.)",false);this.tier=tier;setNode(li.cil.oc.api.Network.newNode(this,Visibility.Network).withConnector(Settings.get().bufferCapacitorUpgrades[tier]).create());}
    @Override public Map<String,String> getDeviceInfo(){
        return Map.of("class","power","description","Battery","vendor",li.cil.oc.Constants.DeviceInfo.DefaultVendor,
                "product","Unlimited Power (Almost Ed.)","capacity",Double.toString(Settings.get().bufferCapacitorUpgrades[tier]));
    }
}
