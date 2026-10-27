package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.OpenComputers;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.server.agent.*;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** The robot control component and its original bundled robot.lua ROM. */
public final class Robot extends AgentComponent {
    private final ManagedEnvironment rom;
    public Robot(AgentHost host){super(host,"robot",Settings.get().bufferRobot);rom=li.cil.oc.api.FileSystem.asManagedEnvironment(li.cil.oc.api.FileSystem.fromClass(OpenComputers.class,"opencomputers","lua/component/robot"),"robot");}
    private RobotController robot(){return (RobotController)agent;}
    @Override public java.util.Map<String,String> getDeviceInfo(){var info=new java.util.LinkedHashMap<>(super.getDeviceInfo());info.put("capacity",Integer.toString(robot().getContainerSize()));return info;}
    @Callback public Object[] getLightColor(Context context,Arguments args){return new Object[]{robot().info.lightColor};}
    @Callback public Object[] setLightColor(Context context,Arguments args){robot().info.lightColor=args.checkInteger(0);agent.synchronize();context.pause(.1);return new Object[]{robot().info.lightColor};}
    @Callback public Object[] durability(Context context,Arguments args){var stack=agent.equipmentInventory().getItem(0);if(stack.isEmpty())return new Object[]{null,"no tool equipped"};var value=li.cil.oc.common.ToolDurabilityProviders.getDurability(stack);return value.isPresent()?new Object[]{value.getAsDouble()}:new Object[]{null,"tool cannot be damaged"};}
    @Callback public Object[] move(Context context,Arguments args){
        int local=args.checkInteger(0);if(local<0||local>3)throw new IllegalArgumentException("invalid side");Direction direction=agent.toGlobal(Direction.from3DDataValue(local));
        if(robot().animationTicks>0&&robot().animationKind==2)return new Object[]{null,"already moving"};
        Object[] contents=blockContent(direction);if(Boolean.TRUE.equals(contents[0])){context.pause(.4);return new Object[]{null,contents[1]};}
        if(!node().tryChangeBuffer(-Settings.get().robotMoveCost))return new Object[]{null,"not enough energy"};
        if(robot().move(direction)){context.pause(agent.delay("move"));return new Object[]{true};}
        node().changeBuffer(Settings.get().robotMoveCost);context.pause(.4);return new Object[]{null,"impossible move"};
    }
    @Callback public Object[] turn(Context context,Arguments args){boolean clockwise=args.checkBoolean(0);if(!node().tryChangeBuffer(-Settings.get().robotTurnCost))return new Object[]{null,"not enough energy"};robot().turn(clockwise);context.pause(agent.delay("turn"));return new Object[]{true};}
    @Override public void onConnect(Node connected){if(connected==node()&&rom!=null&&rom.node()!=null){if(rom.node() instanceof Component component)component.setVisibility(Visibility.Network);node().connect(rom.node());}}
    @Override public void onMessage(Message message){if(message.name().equals("network.message")&&message.source()!=agent.node()&&message.data().length==1&&message.data()[0] instanceof Packet packet&&robot().proxy.externalNode()!=null)robot().proxy.externalNode().sendToReachable("network.message",packet);}
    @Override public void load(CompoundTag tag,HolderLookup.Provider registries){super.load(tag);if(rom!=null)rom.load(tag.getCompound("romRobot"),registries);}
    @Override public void save(CompoundTag tag,HolderLookup.Provider registries){super.save(tag);if(rom!=null){CompoundTag data=new CompoundTag();rom.save(data,registries);tag.put("romRobot",data);}}
}
