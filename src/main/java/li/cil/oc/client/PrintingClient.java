package li.cil.oc.client;

import li.cil.oc.client.gui.PrinterScreen;
import li.cil.oc.client.model.PrintBakedModel;
import li.cil.oc.client.renderer.PrinterBlockEntityRenderer;
import li.cil.oc.common.init.Printing;
import li.cil.oc.common.menu.PrinterMenu;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class PrintingClient {
    private PrintingClient() {}
    public static void register(IEventBus bus) { bus.addListener(PrintingClient::screens); bus.addListener(PrintingClient::renderers); bus.addListener(PrintingClient::models); }
    private static void screens(RegisterMenuScreensEvent event) { event.register(PrinterMenu.TYPE.get(), PrinterScreen::new); }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(Printing.PRINTER_TYPE.get(), PrinterBlockEntityRenderer::new); }
    private static void models(ModelEvent.ModifyBakingResult event) {
        for (var state : Printing.PRINT.get().getStateDefinition().getPossibleStates()) {
            var key = BlockModelShaper.stateToModelLocation(state);
            var model = event.getModels().get(key);
            if (model != null) event.getModels().put(key, new PrintBakedModel(model));
        }
        var key = ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath("opencomputers", "print"));
        var model = event.getModels().get(key); if (model != null) event.getModels().put(key, new PrintBakedModel(model));
    }
}
