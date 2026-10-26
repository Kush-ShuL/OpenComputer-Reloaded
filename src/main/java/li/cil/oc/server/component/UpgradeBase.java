package li.cil.oc.server.component;

import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.network.*;
import li.cil.oc.api.internal.Agent;
import li.cil.oc.api.internal.Tablet;
import net.minecraft.world.entity.player.Player;
import java.util.Map;

/** Common host plumbing for the original built-in upgrades. */
abstract class UpgradeBase extends WorldComponent implements DeviceInfo {
    private final String description, product;
    protected UpgradeBase(EnvironmentHost host, String name, String description, String product, boolean connector) {
        super(host);this.description=description;this.product=product;
        var builder=li.cil.oc.api.Network.newNode(this,Visibility.Network);
        if(name!=null){var component=builder.withComponent(name,host instanceof Agent?Visibility.Neighbors:Visibility.Network);setNode(connector?component.withConnector().create():component.create());}
        else setNode(connector?builder.withConnector().create():builder.create());
    }
    @Override protected Player fakePlayer(){return host instanceof Agent agent?agent.player():host instanceof Tablet tablet?tablet.player():super.fakePlayer();}
    @Override public Map<String,String> getDeviceInfo(){return Map.of("class","generic","description",description,"vendor",li.cil.oc.Constants.DeviceInfo.DefaultVendor,"product",product);}
    protected void changed(){host.markChanged();}
    protected Connector connector(){return (Connector)node();}
}
