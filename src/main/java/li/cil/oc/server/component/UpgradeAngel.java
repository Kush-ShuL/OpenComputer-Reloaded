package li.cil.oc.server.component;

import java.util.HashMap;
import java.util.Map;
import li.cil.oc.Settings;
import li.cil.oc.api.network.EnvironmentHost;

/** Permission is applied by the original RobotPlaceInAirEvent hook. */
public final class UpgradeAngel extends UpgradeBase {
    public UpgradeAngel(EnvironmentHost host){super(host,null,"Angel upgrade","FreePlacer (TM)",false);}
    @Override public Map<String,String> getDeviceInfo(){
        Map<String,String> info=new HashMap<>(super.getDeviceInfo());
        info.put("capacity",Integer.toString(Settings.get().maxNetworkPacketSize));
        return info;
    }
}
