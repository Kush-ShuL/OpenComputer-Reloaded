package li.cil.oc.server.component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.component.RackBusConnectable;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.internal.Rack;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Packet;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.nbt.CompoundTag;

/** Wired modem ports, packet delivery and wake-up messages. */
public class NetworkCard extends AbstractManagedEnvironment implements RackBusConnectable, DeviceInfo {
    protected final EnvironmentHost host;
    protected final int wirelessTier;
    protected final Visibility visibility;
    private final Set<Integer> ports = new LinkedHashSet<>();
    private String wakeMessage;
    private boolean fuzzy;

    public NetworkCard(EnvironmentHost host) { this(host, -1); }
    protected NetworkCard(EnvironmentHost host, int wirelessTier) {
        this.host = Objects.requireNonNull(host); this.wirelessTier = wirelessTier;
        visibility = wirelessTier >= 0 ? Visibility.Network : host instanceof Rack ? Visibility.Neighbors : Visibility.Network;
        var builder = Network.newNode(this, visibility).withComponent("modem", Visibility.Neighbors);
        setNode(wirelessTier >= 0 ? builder.withConnector().create() : builder.create());
    }
    protected int maxOpenPorts() { return Settings.get().maxOpenPorts[wirelessTier + 1]; }
    @Override public Map<String, String> getDeviceInfo() {
        return Map.of("class","network","description","Ethernet controller","vendor","MightyPirates GmbH & Co. KG",
                "product","42i520 (MPN-01)","version","1.0","capacity",Integer.toString(Settings.get().maxNetworkPacketSize),
                "size",Integer.toString(maxOpenPorts()),"width",Integer.toString(Settings.get().maxNetworkPacketParts));
    }
    @Callback(doc="function(port:number):boolean -- Open a modem port.")
    public synchronized Object[] open(Context context, Arguments args) throws IOException {
        int port = checkPort(args.checkInteger(0));
        if (ports.contains(port)) return new Object[]{false};
        if (ports.size() >= maxOpenPorts()) throw new IOException("too many open ports");
        ports.add(port); host.markChanged(); return new Object[]{true};
    }
    @Callback(doc="function([port:number]):boolean -- Close one port, or every open port.")
    public synchronized Object[] close(Context context, Arguments args) {
        boolean changed;
        if (args.count() == 0) { changed = !ports.isEmpty(); ports.clear(); }
        else changed = ports.remove(checkPort(args.checkInteger(0)));
        if (changed) host.markChanged(); return new Object[]{changed};
    }
    @Callback(direct=true,doc="function(port:number):boolean -- Test whether a port is open.")
    public synchronized Object[] isOpen(Context context, Arguments args) { return new Object[]{ports.contains(checkPort(args.checkInteger(0)))}; }
    @Callback(direct=true,doc="function():boolean -- Test wireless capability.")
    public Object[] isWireless(Context context, Arguments args) { return new Object[]{false}; }
    @Callback(direct=true,doc="function():boolean -- Test wired capability.")
    public Object[] isWired(Context context, Arguments args) { return new Object[]{true}; }
    @Callback(doc="function(address:string,port:number,data...):boolean -- Send a packet to one address.")
    public Object[] send(Context context, Arguments args) throws IOException {
        Packet packet = Network.newPacket(node().address(), args.checkString(0), checkPort(args.checkInteger(1)), tail(args, 2));
        doSend(packet); activity(); return new Object[]{true};
    }
    @Callback(doc="function(port:number,data...):boolean -- Broadcast a packet.")
    public Object[] broadcast(Context context, Arguments args) throws IOException {
        Packet packet = Network.newPacket(node().address(), null, checkPort(args.checkInteger(0)), tail(args, 1));
        doSend(packet); activity(); return new Object[]{true};
    }
    protected void doSend(Packet packet) throws IOException {
        if (visibility == Visibility.Neighbors) node().sendToNeighbors("network.message", packet);
        else if (visibility == Visibility.Network) node().sendToReachable("network.message", packet);
    }
    @Callback(direct=true,doc="function():string,boolean -- Get the wake-up message and fuzzy flag.")
    public synchronized Object[] getWakeMessage(Context context, Arguments args) { return new Object[]{wakeMessage, fuzzy}; }
    @Callback(doc="function(message:string[,fuzzy:boolean]):string,boolean -- Set the wake-up message.")
    public synchronized Object[] setWakeMessage(Context context, Arguments args) {
        Object[] previous = {wakeMessage, fuzzy};
        wakeMessage = args.optAny(0, null) == null ? null : args.checkString(0);
        fuzzy = args.optBoolean(1, fuzzy); host.markChanged(); return previous;
    }
    @Override public synchronized void onDisconnect(Node node) { if (node == node()) ports.clear(); }
    @Override public synchronized void onMessage(Message message) {
        if ((message.name().equals("computer.stopped") || message.name().equals("computer.started")) && node().isNeighborOf(message.source())) ports.clear();
        if (message.name().equals("network.message") && message.data().length == 1 && message.data()[0] instanceof Packet packet) receivePacket(packet);
    }
    @Override public void receivePacket(Packet packet) { receive(packet, 0); }
    protected synchronized boolean accepts(Packet packet, double distance) { return ports.contains(packet.port()); }
    protected synchronized void receive(Packet packet, double distance) {
        if (Objects.equals(packet.source(), node().address()) || packet.destination() != null && !packet.destination().equals(node().address())) return;
        if (accepts(packet, distance)) {
            Object[] payload = packet.data();
            Object[] signal = new Object[payload.length + 4];
            signal[0] = "modem_message"; signal[1] = packet.source(); signal[2] = packet.port(); signal[3] = distance;
            System.arraycopy(payload, 0, signal, 4, payload.length);
            node().sendToReachable("computer.signal", signal); activity();
        }
        Object[] data = packet.data();
        if (wakeMessage != null && data.length > 0 && (fuzzy || data.length == 1)) {
            String message = data[0] instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : data[0] instanceof String text ? text : null;
            if (wakeMessage.equals(message)) {
                if (host instanceof Context context) context.start();
                else node().sendToNeighbors("computer.start");
            }
        }
    }
    protected void activity() { li.cil.oc.server.PacketSender.sendNetworkActivity(node(), host); }
    protected static int checkPort(int port) { if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid port number"); return port; }
    private static Object[] tail(Arguments args, int offset) { Object[] data = new Object[args.count() - offset]; for (int i=0;i<data.length;i++) data[i]=args.checkAny(i+offset); return data; }
    @Override public synchronized void load(CompoundTag tag) {
        super.load(tag); ports.clear();
        for (int port : tag.getIntArray("openPorts")) if (port >= 1 && port <= 65535 && ports.size() < maxOpenPorts()) ports.add(port);
        wakeMessage = tag.contains("wakeMessage") ? tag.getString("wakeMessage") : null; fuzzy = tag.getBoolean("wakeMessageFuzzy");
    }
    @Override public synchronized void save(CompoundTag tag) {
        super.save(tag); tag.putIntArray("openPorts", ports.stream().mapToInt(Integer::intValue).toArray());
        if (wakeMessage != null) tag.putString("wakeMessage", wakeMessage); else tag.remove("wakeMessage");
        tag.putBoolean("wakeMessageFuzzy", fuzzy);
    }
}
