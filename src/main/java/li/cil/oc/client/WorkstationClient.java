package li.cil.oc.client;

import li.cil.oc.client.gui.WorkstationScreen;
import li.cil.oc.client.renderer.WorkstationBlockEntityRenderer;
import li.cil.oc.common.init.Workstations;
import li.cil.oc.common.menu.WorkstationMenu;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class WorkstationClient {
    private WorkstationClient() {}
    public static void initialize(IEventBus bus) { bus.addListener(WorkstationClient::screens); bus.addListener(WorkstationClient::renderers); }
    private static void screens(RegisterMenuScreensEvent event) { event.register(WorkstationMenu.TYPE.get(), WorkstationScreen::new); }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Workstations.ASSEMBLER_TYPE.get(), context -> new WorkstationBlockEntityRenderer<>());
        event.registerBlockEntityRenderer(Workstations.DISASSEMBLER_TYPE.get(), context -> new WorkstationBlockEntityRenderer<>());
    }
}
