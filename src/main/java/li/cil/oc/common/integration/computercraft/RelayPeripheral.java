package li.cil.oc.common.integration.computercraft;

import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.*;
import li.cil.oc.common.block.entity.RelayBlockEntity;
import net.minecraft.core.Direction;

/** The original modem facade and remote OC component calls, using the current CC API. */
public final class RelayPeripheral implements IDynamicPeripheral,RelayBlockEntity.PacketListener {
    private static final String[] METHODS={"callRemote","close","closeAll","getMethodsRemote","getNamesRemote","getTypeRemote","isAccessPoint","isOpen","isPresentRemote","isTunnel","isWireless","maxPacketSize","open","transmit"};
    private final RelayBlockEntity relay;
    private final Map<IComputerAccess,Set<Integer>> openPorts=new IdentityHashMap<>();
    RelayPeripheral(RelayBlockEntity relay){this.relay=relay;}
    @Override public String getType(){return "modem";}
    @Override public String[] getMethodNames(){return METHODS.clone();}
    @Override public boolean equals(IPeripheral other){return other instanceof RelayPeripheral peripheral&&peripheral.relay==relay;}
    @Override public synchronized void attach(IComputerAccess computer){if(openPorts.isEmpty())relay.addPacketListener(this);openPorts.computeIfAbsent(computer,key->new HashSet<>());}
    @Override public synchronized void detach(IComputerAccess computer){openPorts.remove(computer);if(openPorts.isEmpty())relay.removePacketListener(this);}
    @Override public synchronized void removed(){openPorts.clear();relay.removePacketListener(this);}
    @Override public MethodResult callMethod(IComputerAccess computer,ILuaContext context,int method,IArguments arguments)throws LuaException{
        if(method<0||method>=METHODS.length)throw new LuaException("no such method");
        Object[] values=arguments.getAll();
        return context.executeMainThreadTask(()->{try{return invoke(computer,context,METHODS[method],new ObjectArguments(values));}catch(LuaException error){throw error;}catch(Exception error){throw new LuaException(Objects.toString(error.getMessage(),error.toString()));}});
    }
    private static int port(IArguments args,int index)throws LuaException{int port=(int)args.getDouble(index);if(port<0||port>65535)throw new LuaException("bad argument #"+(index+1)+" (number in [1, 65535] expected)");return port;}
    private synchronized Set<Integer> ports(IComputerAccess computer)throws LuaException{Set<Integer> ports=openPorts.get(computer);if(ports==null)throw new LuaException("peripheral is not attached");return ports;}
    private List<li.cil.oc.api.network.Component> visible(){
        Map<String,li.cil.oc.api.network.Component> result=new LinkedHashMap<>();
        for(Direction side:Direction.values()){Node node=relay.sidedNode(side);if(node==null||node.network()==null)continue;for(Node candidate:node.reachableNodes())if(candidate instanceof li.cil.oc.api.network.Component component&&component.canBeSeenFrom(node))result.putIfAbsent(component.address(),component);}
        return new ArrayList<>(result.values());
    }
    private static Map<Integer,Object> array(Collection<?> values){Map<Integer,Object> result=new LinkedHashMap<>();int index=1;for(Object value:values)result.put(index++,value);return result;}
    private Object[] invoke(IComputerAccess computer,ILuaContext context,String method,IArguments args)throws Exception{
        if(relay.isRemoved()||relay.getLevel()==null)throw new LuaException("peripheral is detached");
        switch(method){
            case "open" -> {int port=port(args,0);synchronized(this){Set<Integer> ports=ports(computer);if(ports.size()>=128)throw new LuaException("too many open channels");return new Object[]{ports.add(port)};}}
            case "isOpen" -> {int port=port(args,0);synchronized(this){return new Object[]{ports(computer).contains(port)};}}
            case "close" -> {int port=port(args,0);synchronized(this){return new Object[]{ports(computer).remove(port)};}}
            case "closeAll" -> {synchronized(this){ports(computer).clear();}return null;}
            case "transmit" -> {int send=port(args,0),reply=port(args,1);Object[] supplied=args.drop(2).getAll(),data=new Object[supplied.length+1];data[0]=(double)reply;System.arraycopy(supplied,0,data,1,supplied.length);return new Object[]{relay.tryEnqueuePacket(null,Network.newPacket("cc"+computer.getID()+"_"+computer.getAttachmentName(),null,send,data))};}
            case "isWireless" -> {return new Object[]{false};}
            case "isAccessPoint" -> {return new Object[]{relay.wirelessEnabled()};}
            case "isTunnel" -> {return new Object[]{relay.linkedEnabled()};}
            case "maxPacketSize" -> {return new Object[]{Settings.get().maxNetworkPacketSize};}
            case "getNamesRemote" -> {return new Object[]{array(visible().stream().map(Node::address).toList())};}
            default -> {
                String address=args.getString(0);li.cil.oc.api.network.Component component=visible().stream().filter(value->Objects.equals(value.address(),address)).findFirst().orElse(null);
                if(method.equals("isPresentRemote"))return new Object[]{component!=null};if(component==null)return null;
                if(method.equals("getTypeRemote"))return new Object[]{component.name()};
                if(method.equals("getMethodsRemote"))return new Object[]{array(component.methods())};
                if(method.equals("callRemote")){
                    // Supply the physical relay level's registry lookup, including for non-Machine CC contexts.
                    Object[] result=li.cil.oc.server.machine.Callbacks.invoke(component.host(),args.getString(1),new CCContext(computer),args.drop(2).getAll());
                    return li.cil.oc.server.driver.Registry.convert(result,relay.getLevel().registryAccess());
                }
                throw new LuaException("no such method");
            }
        }
    }
    @Override public synchronized void receive(Packet packet){
        Object[] data=packet.data();boolean reply=data.length>0&&data[0] instanceof Double;int replyPort=reply?((Double)data[0]).intValue():-1;int offset=reply?1:0;
        List<IComputerAccess> stale=new ArrayList<>();
        for(var entry:openPorts.entrySet()){
            try {
            IComputerAccess computer=entry.getKey();String address="cc"+computer.getID()+"_"+computer.getAttachmentName();
            if(Objects.equals(packet.source(),address)||packet.destination()!=null&&!packet.destination().equals(address)||!entry.getValue().contains(packet.port()))continue;
            Object[] event=new Object[data.length-offset+3];event[0]=computer.getAttachmentName();event[1]=packet.port();event[2]=replyPort;
            for(int i=offset;i<data.length;i++)event[i-offset+3]=data[i] instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):data[i];
            computer.queueEvent("modem_message",event);
            }catch(NotAttachedException ignored){stale.add(entry.getKey());}
        }
        stale.forEach(openPorts::remove);if(openPorts.isEmpty())relay.removePacketListener(this);
    }
    private final class CCContext implements Context {
        private final IComputerAccess computer;CCContext(IComputerAccess computer){this.computer=computer;}
        @Override public Node node(){return relay.node();}
        @Override public boolean canInteract(String player){return true;}
        @Override public boolean isRunning(){return true;}
        @Override public boolean isPaused(){return false;}
        @Override public boolean start(){return false;}
        @Override public boolean pause(double seconds){return false;}
        @Override public boolean stop(){return false;}
        @Override public void consumeCallBudget(double cost){}
        @Override public boolean signal(String name,Object...args){computer.queueEvent(name,args);return true;}
    }
}
