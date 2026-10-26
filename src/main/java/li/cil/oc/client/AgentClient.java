package li.cil.oc.client;

import li.cil.oc.common.init.Agents;
import li.cil.oc.common.menu.AgentMenu;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.server.agent.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import li.cil.oc.common.network.AgentNetworking;
import java.util.*;

/** Physical-client registration and stable mirrors for a robot's moving proxy. */
public final class AgentClient {
    private static final Map<String,RobotController> ROBOTS=new HashMap<>();
    private AgentClient(){}
    public static void register(IEventBus bus){bus.addListener(AgentClient::renderers);bus.addListener(AgentClient::screens);bus.addListener(AgentClient::extensions);NeoForge.EVENT_BUS.addListener(AgentClient::unload);}
    private static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(Agents.ROBOT_TYPE.get(),li.cil.oc.client.renderer.RobotBlockEntityRenderer::new);event.registerEntityRenderer(Agents.DRONE_TYPE.get(),li.cil.oc.client.renderer.DroneRenderer::new);}
    private static void screens(RegisterMenuScreensEvent event){event.register(AgentMenu.TYPE.get(),li.cil.oc.client.gui.AgentScreen::new);}
    private static void extensions(RegisterClientExtensionsEvent event){event.registerItem(new IClientItemExtensions(){private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;@Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new li.cil.oc.client.renderer.AgentItemRenderer();return renderer;}},Agents.ROBOT_ITEM.get(),Agents.DRONE.get());}
    private static void unload(LevelEvent.Unload event){if(event.getLevel().isClientSide())ROBOTS.clear();}
    public static void applyScreen(RobotController host,CompoundTag tag){
        String address=tag.getString("oc:address");
        if(!address.isEmpty()){RobotController previous=ROBOTS.get(address);if(previous!=null&&previous!=host){var proxy=host.proxy;proxy.attach(previous);host=previous;host.load(tag,proxy.getLevel().registryAccess());}ROBOTS.put(address,host);}
        host.clientHasKeyboard=tag.getBoolean("agentKeyboard");
        if(!tag.contains("agentBuffer")){host.clientScreen=null;return;}
        if(host.clientScreen==null)host.clientScreen=new TextBuffer(host,new AgentDisplayServices(host));
        TextBuffer buffer=host.clientScreen;buffer.setMaximumResolution(Math.max(1,tag.getInt("agentMaxWidth")),Math.max(1,tag.getInt("agentMaxHeight")));buffer.data().load(tag.getCompound("agentBuffer"));buffer.setViewport(Math.clamp(tag.getInt("agentViewportWidth"),1,buffer.data().width),Math.clamp(tag.getInt("agentViewportHeight"),1,buffer.data().height));buffer.applyPowerState(tag.getBoolean("agentPower"));buffer.markInitialized();buffer.dirty=true;
    }
    public static void sendInput(String name,Object...values){var player=net.minecraft.client.Minecraft.getInstance().player;if(player==null||!(player.containerMenu instanceof AgentMenu menu))return;AgentNetworking.Action action;int character=0,code=0,value=0;double x=0,y=0;String text="";
        switch(name){case "request"->action=AgentNetworking.Action.REQUEST;case "keyboard.keyDown","keyboard.keyUp"->{action=name.endsWith("Down")?AgentNetworking.Action.KEY_DOWN:AgentNetworking.Action.KEY_UP;character=(Character)values[0];code=((Number)values[1]).intValue();}case "keyboard.clipboard"->{action=AgentNetworking.Action.CLIPBOARD;text=(String)values[0];}case "touch","drag","drop","scroll"->{action=switch(name){case "touch"->AgentNetworking.Action.TOUCH;case "drag"->AgentNetworking.Action.DRAG;case "drop"->AgentNetworking.Action.DROP;default->AgentNetworking.Action.MOUSE_SCROLL;};x=((Number)values[0]).doubleValue();y=((Number)values[1]).doubleValue();value=((Number)values[2]).intValue();}default->{return;}}
        PacketDistributor.sendToServer(new AgentNetworking.Input(menu.containerId,action,character,code,x,y,value,text));
    }
}
