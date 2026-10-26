package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.common.menu.AdapterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = "opencomputers", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AdapterScreen extends AbstractContainerScreen<AdapterMenu> {
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("opencomputers", "textures/" + name + ".png"); }
    private static final ResourceLocation BACKGROUND = texture("gui/background"), SLOT = texture("gui/slot"), UPGRADE = texture("icons/upgrade");
    public AdapterScreen(AdapterMenu menu, Inventory player, Component title) { super(menu, player, title); imageWidth = 176; imageHeight = 166; }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) { event.register(AdapterMenu.TYPE.get(), AdapterScreen::new); }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        graphics.blit(SLOT, leftPos + 79, topPos + 34, 0, 0, 18, 18, 18, 18);
        if (!menu.slots.getFirst().hasItem()) graphics.blit(UPGRADE, leftPos + 80, topPos + 35, 0, 0, 16, 16, 16, 16);
        RenderSystem.disableBlend();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) { super.render(graphics, mouseX, mouseY, partial); renderTooltip(graphics, mouseX, mouseY); }
}
