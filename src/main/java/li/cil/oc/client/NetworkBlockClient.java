package li.cil.oc.client;

import li.cil.oc.client.gui.*;
import li.cil.oc.client.renderer.NetworkDeviceRenderer;
import li.cil.oc.common.block.entity.RelayBlockEntity;
import li.cil.oc.common.init.NetworkBlocks;
import li.cil.oc.common.network.NetworkBlockNetworking;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;

public final class NetworkBlockClient {
    private NetworkBlockClient(){}
    public static void register(IEventBus bus){bus.addListener(NetworkBlockClient::menus);bus.addListener(NetworkBlockClient::renderers);}
    private static void menus(RegisterMenuScreensEvent event){event.register(NetworkBlocks.RELAY_MENU.get(),RelayScreen::new);}
    private static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(NetworkBlocks.RELAY_TYPE.get(),NetworkDeviceRenderer.Relay::new);event.registerBlockEntityRenderer(NetworkBlocks.NET_SPLITTER_TYPE.get(),NetworkDeviceRenderer.Splitter::new);}
    public static void relayActivity(NetworkBlockNetworking.RelayActivity packet){var level=Minecraft.getInstance().level;if(level!=null&&level.dimension().location().equals(packet.dimension())&&level.getBlockEntity(packet.pos()) instanceof RelayBlockEntity relay)relay.lastMessage=System.currentTimeMillis();}
    public static void openWaypoint(NetworkBlockNetworking.WaypointOpen packet){var minecraft=Minecraft.getInstance();if(minecraft.level!=null&&minecraft.level.dimension().location().equals(packet.dimension()))minecraft.setScreen(new WaypointScreen(packet));}
}
