package li.cil.oc.server.component;

import li.cil.oc.api.network.EnvironmentHost;

/** Permission is applied by the original RobotPlaceInAirEvent hook. */
public final class UpgradeAngel extends UpgradeBase {
    public UpgradeAngel(EnvironmentHost host){super(host,null,"Angel upgrade","FreePlacer",false);}
}
