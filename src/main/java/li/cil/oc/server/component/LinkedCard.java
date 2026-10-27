package li.cil.oc.server.component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Packet;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.server.network.QuantumNetwork;
import net.minecraft.nbt.CompoundTag;

/** Original paired quantum-channel card, including wake-up and energy rules. */
public final class LinkedCard extends AbstractManagedEnvironment implements QuantumNetwork.QuantumNode,DeviceInfo {
    private final EnvironmentHost host;
    private String channel="creative",wakeMessage;
    private boolean fuzzy;
    public LinkedCard(EnvironmentHost host) {
        this.host=host;
        setNode(Network.newNode(this,Visibility.Network).withComponent("tunnel",Visibility.Neighbors).withConnector().create());
    }
    @Override public String tunnel() { return channel; }
    @Override public Map<String,String> getDeviceInfo() {
        return Map.of("class","network","description","Quantumnet controller","vendor","MightyPirates GmbH & Co. KG","product","HyperLink IV: Ender Edition",
                "capacity",Integer.toString(Settings.get().maxNetworkPacketSize),"width",Integer.toString(Settings.get().maxNetworkPacketParts));
    }
    @Callback(doc="function(data...):boolean -- Send a packet to every card linked to this channel.")
    public Object[] send(Context context,Arguments args) {
        Object[] data=new Object[args.count()]; for(int i=0;i<data.length;i++) data[i]=args.checkAny(i);
        Packet packet=Network.newPacket(node().address(),null,0,data);
        double cost=packet.size()/32.0+Settings.get().wirelessCostPerRange[1]*Settings.get().maxWirelessRange[1]*5;
        if(!((Connector)node()).tryChangeBuffer(-cost)) return new Object[]{null,"not enough energy"};
        for(var endpoint:QuantumNetwork.getEndpoints(channel)) if(endpoint!=this) endpoint.receivePacket(packet);
        return new Object[]{true};
    }
    @Callback(direct=true,doc="function():number -- Maximum packet size.")
    public Object[] maxPacketSize(Context context,Arguments args) { return new Object[]{Settings.get().maxNetworkPacketSize}; }
    @Callback(direct=true,doc="function():string -- Shared channel address.")
    public Object[] getChannel(Context context,Arguments args) { return new Object[]{channel}; }
    @Callback(direct=true,doc="function():string,boolean -- Get the current wake-up message.")
    public synchronized Object[] getWakeMessage(Context context,Arguments args) { return new Object[]{wakeMessage,fuzzy}; }
    @Callback(doc="function(message:string[,fuzzy:boolean]):string,boolean -- Set the wake-up message.")
    public synchronized Object[] setWakeMessage(Context context,Arguments args) {
        Object[] old={wakeMessage,fuzzy}; wakeMessage=args.optAny(0,null)==null?null:args.checkString(0); fuzzy=args.optBoolean(1,fuzzy); host.markChanged(); return old;
    }
    @Override public synchronized void receivePacket(Packet packet) {
        if(Objects.equals(packet.source(),node().address())||packet.destination()!=null&&!packet.destination().equals(node().address())) return;
        Object[] data=packet.data(),signal=new Object[data.length+4];
        signal[0]="modem_message"; signal[1]=packet.source(); signal[2]=packet.port(); signal[3]=0.0;
        System.arraycopy(data,0,signal,4,data.length); node().sendToReachable("computer.signal",signal);
        if(wakeMessage!=null&&data.length>0&&(fuzzy||data.length==1)) {
            String message=data[0] instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):data[0] instanceof String text?text:null;
            if(wakeMessage.equals(message)) node().sendToNeighbors("computer.start");
        }
    }
    @Override public void onConnect(Node node) { if(node==node()) QuantumNetwork.add(this); }
    @Override public void onDisconnect(Node node) { if(node==node()) QuantumNetwork.remove(this); }
    @Override public synchronized void load(CompoundTag tag) {
        boolean connected=node().network()!=null; if(connected) QuantumNetwork.remove(this);
        super.load(tag); if(tag.contains("oc:tunnel"))channel=tag.getString("oc:tunnel");
        if(tag.contains("wakeMessage"))wakeMessage=tag.getString("wakeMessage"); fuzzy=tag.getBoolean("wakeMessageFuzzy");
        if(connected) QuantumNetwork.add(this);
    }
    @Override public synchronized void save(CompoundTag tag) {
        super.save(tag); tag.putString("oc:tunnel",channel);
        if(wakeMessage!=null) tag.putString("wakeMessage",wakeMessage); else tag.remove("wakeMessage"); tag.putBoolean("wakeMessageFuzzy",fuzzy);
    }
}
