package li.cil.oc.client;

import li.cil.oc.common.menu.DatabaseMenu;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class AgentUpgradeClient {
    private AgentUpgradeClient(){}
    public static void register(IEventBus bus){bus.addListener(AgentUpgradeClient::screens);}
    private static void screens(RegisterMenuScreensEvent event){event.register(DatabaseMenu.TYPE.get(),li.cil.oc.client.gui.DatabaseScreen::new);}
}
