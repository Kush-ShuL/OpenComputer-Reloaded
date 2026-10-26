package li.cil.oc.common.item;

import li.cil.oc.OpenComputers;
import li.cil.oc.api.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.*;

public final class DebuggerItem extends Item {
    private static final Monitor MONITOR=new Monitor();
    public DebuggerItem(Properties properties){super(properties);}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){if(context.getPlayer()==null||context.getPlayer() instanceof FakePlayer)return InteractionResult.PASS;if(!context.getLevel().isClientSide){var block=context.getLevel().getBlockEntity(context.getClickedPos());Node target=block instanceof SidedEnvironment sided?sided.sidedNode(context.getClickedFace()):block instanceof Environment environment?environment.node():null;MONITOR.reconnect(target);}return InteractionResult.sidedSuccess(context.getLevel().isClientSide);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);tooltip.add(Component.translatable("oc:tooltip.debugger"));}
    public static void stop(net.neoforged.neoforge.event.server.ServerStoppedEvent event){MONITOR.reconnect(null);}
    private static final class Monitor implements Environment {
        private Node node;
        public Node node(){return node;}
        private void reconnect(Node target){if(node!=null)node.remove();if(target==null){node=null;return;}if(node==null)node=li.cil.oc.api.Network.newNode(this,Visibility.Network).create();li.cil.oc.api.Network.joinNewNetwork(node);node.connect(target);}
        public void onConnect(Node connected){OpenComputers.LOGGER.info("[NETWORK DEBUGGER] New node in network: {}",info(connected));}
        public void onDisconnect(Node disconnected){OpenComputers.LOGGER.info("[NETWORK DEBUGGER] Node removed from network: {}",info(disconnected));}
        public void onMessage(Message message){OpenComputers.LOGGER.info("[NETWORK DEBUGGER] Received message: {{name = {}, source = {}, data = {}}}",message.name(),info(message.source()),Arrays.deepToString(message.data()));}
        private String info(Node value){String result="{address = "+value.address()+", reachability = "+value.reachability();if(value instanceof li.cil.oc.api.network.Component component)result+=", type = component, name = "+component.name()+", visibility = "+component.visibility();if(value instanceof Connector connector)result+=", type = connector, buffer = "+connector.localBuffer()+", bufferSize = "+connector.localBufferSize();return result+"}";}
    }
}
