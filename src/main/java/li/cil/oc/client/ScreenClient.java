package li.cil.oc.client;

import li.cil.oc.OpenComputers;
import li.cil.oc.client.gui.DriveConfigurationScreen;
import li.cil.oc.client.gui.ScreenGui;
import li.cil.oc.client.renderer.ScreenBlockEntityRenderer;
import li.cil.oc.client.renderer.ScreenTextRenderer;
import li.cil.oc.client.renderer.ScreenBakedModel;
import li.cil.oc.common.block.entity.ScreenBlockEntity;
import li.cil.oc.common.init.Blocks;
import li.cil.oc.common.item.ComponentItem;
import li.cil.oc.common.network.ScreenNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = OpenComputers.ID, value = Dist.CLIENT)
public final class ScreenClient {
    private static final Map<BlockPos, Long> REQUESTS = new HashMap<>();
    private static final Map<BlockPos, ScreenNetworking.Sync> PENDING = new HashMap<>();
    private static Level previousLevel;
    private ScreenClient() {}
    public static void request(ScreenBlockEntity screen) {
        var level = Minecraft.getInstance().level; if (level == null || Minecraft.getInstance().getConnection() == null) return;
        long time = level.getGameTime();
        Long previous = REQUESTS.get(screen.getBlockPos());
        if (previous != null && time >= previous && time - previous < 20) return;
        REQUESTS.put(screen.getBlockPos(), time);
        PacketDistributor.sendToServer(new ScreenNetworking.Request(screen.getBlockPos()));
    }
    public static void forget(ScreenBlockEntity screen) { REQUESTS.remove(screen.getBlockPos()); PENDING.remove(screen.getBlockPos()); }
    public static void receive(ScreenNetworking.Sync packet) {
        var level = Minecraft.getInstance().level; if (level == null) return;
        if (!(level.getBlockEntity(packet.position()) instanceof ScreenBlockEntity screen)) {
            if (PENDING.size() < 256) PENDING.put(packet.position(), packet); return;
        }
        try {
            if (packet.full()) { if (packet.revision() >= screen.revision()) screen.applySnapshot(packet.data(), packet.revision()); }
            else screen.applyCommands(packet.data(), packet.revision());
        } catch (RuntimeException error) { OpenComputers.LOGGER.warn("Screen update rejected at {}", packet.position(), error); request(screen); }
    }
    public static void open(ScreenNetworking.Open packet) {
        var client = Minecraft.getInstance(); if (client.level == null) return;
        if (client.level.getBlockEntity(packet.position()) instanceof ScreenBlockEntity screen) {
            screen.applySnapshot(packet.data(), packet.revision());
            client.setScreen(new ScreenGui(screen, packet.session()));
        }
    }
    public static void sendInput(ScreenBlockEntity entity, String type, Object... payload) {
        if (!(Minecraft.getInstance().screen instanceof ScreenGui gui) || !gui.position().equals(entity.origin().getBlockPos())) return;
        switch (type) {
            case "keyboard.keyDown" -> gui.send(ScreenNetworking.InputType.KEY_DOWN, (Character) payload[0], (Integer) payload[1], 0, 0, 0, "");
            case "keyboard.keyUp" -> gui.send(ScreenNetworking.InputType.KEY_UP, (Character) payload[0], (Integer) payload[1], 0, 0, 0, "");
            case "keyboard.clipboard" -> gui.send(ScreenNetworking.InputType.CLIPBOARD, 0, 0, 0, 0, 0, (String) payload[0]);
            case "touch", "drag", "drop", "scroll" -> gui.send(switch (type) { case "touch" -> ScreenNetworking.InputType.TOUCH; case "drag" -> ScreenNetworking.InputType.DRAG; case "drop" -> ScreenNetworking.InputType.DROP; default -> ScreenNetworking.InputType.SCROLL; }, 0, 0, ((Number) payload[0]).doubleValue(), ((Number) payload[1]).doubleValue(), ((Number) payload[2]).intValue(), "");
            case "copyToAnalyzer" -> gui.send(ScreenNetworking.InputType.COPY_LINE, 0, 0, 0, 0, (Integer) payload[0], "");
            default -> { }
        }
    }
    public static boolean touchWorld(ScreenBlockEntity screen,net.minecraft.world.phys.Vec3 point,int projectile){
        Minecraft client=Minecraft.getInstance();
        if(client.player==null||client.getConnection()==null||screen.tier==0)return false;
        double[] coordinates=screen.toScreenCoordinates(point);
        if(coordinates==null)return false;
        PacketDistributor.sendToServer(new ScreenNetworking.WorldTouch(screen.getBlockPos(),coordinates[0],coordinates[1],projectile));
        return true;
    }
    @SubscribeEvent public static void highlight(net.neoforged.neoforge.client.event.RenderHighlightEvent.Block event){
        Minecraft client=Minecraft.getInstance();
        if(client.level!=null&&client.player!=null&&!client.player.isShiftKeyDown()
                &&client.level.getBlockState(event.getTarget().getBlockPos()).getBlock() instanceof li.cil.oc.common.block.ScreenBlock)event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level != previousLevel) { previousLevel = level; REQUESTS.clear(); PENDING.clear(); }
        if (level == null) return;
        for (var entry : PENDING.entrySet().toArray(Map.Entry[]::new)) {
            BlockPos pos = (BlockPos) entry.getKey();
            if (level.getBlockEntity(pos) instanceof ScreenBlockEntity) { ScreenNetworking.Sync packet = PENDING.remove(pos); receive(packet); }
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { REQUESTS.clear(); PENDING.clear(); previousLevel = null; }

    @EventBusSubscriber(modid = OpenComputers.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            for (var type : Blocks.SCREEN_TYPES) event.registerBlockEntityRenderer(type.get(), ScreenBlockEntityRenderer::new);
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) { event.registerReloadListener(ScreenTextRenderer.INSTANCE); }
        @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event) {
            ScreenBakedModel.clearCache();
            event.getModels().replaceAll((location, model) -> {
                String path = location.id().getPath();
                return location.id().getNamespace().equals(OpenComputers.ID) && !location.variant().equals("inventory") &&
                        (path.equals("screen1") || path.equals("screen2") || path.equals("screen3")) ? new ScreenBakedModel(model) : model;
            });
        }
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ComponentItem.setDriveUiOpener((player, hand) -> Minecraft.getInstance().setScreen(new DriveConfigurationScreen(player, hand)));
                var floppy = li.cil.oc.api.Items.get("floppy");
                if (floppy != null) ItemProperties.register(floppy.item(), ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "color"), (stack, level, entity, seed) -> {
                    var data = stack.get(DataComponents.CUSTOM_DATA);
                    var tag = data == null ? null : data.copyTag();
                    return Math.clamp(tag != null && tag.contains("oc:color") ? tag.getInt("oc:color") : 8, 0, 15) / 15F;
                });
                for (String name : new String[]{"tablet"}) {
                    var info = li.cil.oc.api.Items.get(name);
                    if (info != null) ItemProperties.register(info.item(), ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "running"), (stack, level, entity, seed) -> {
                        var data = stack.get(DataComponents.CUSTOM_DATA);
                        return data != null && data.copyTag().getBoolean("oc:isRunning") ? 1 : 0;
                    });
                }
            });
        }
    }
}
