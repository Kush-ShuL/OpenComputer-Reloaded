package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.common.item.data.NavigationUpgradeData;
import li.cil.oc.server.network.Waypoints;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class UpgradeNavigation extends UpgradeBase {
    private final NavigationUpgradeData data=new NavigationUpgradeData();
    public UpgradeNavigation(EnvironmentHost host){super(host,"navigation","Navigation upgrade","PathFinder v3",true);}
    @Callback public Object[] getPosition(Context context,Arguments args){var map=data.mapData(host.world());int size=data.getSize(host.world());double x=host.xPosition()-map.centerX,z=host.zPosition()-map.centerZ;return Math.abs(x)<=size/2d&&Math.abs(z)<=size/2d?new Object[]{x,host.yPosition(),z}:new Object[]{null,"out of range"};}
    @Callback public Object[] getFacing(Context context,Arguments args){return new Object[]{((Rotatable)host).facing().get3DDataValue()};}
    @Callback public Object[] getRange(Context context,Arguments args){return new Object[]{data.getSize(host.world())/2};}
    @Callback public Object[] findWaypoints(Context context,Arguments args){double range=Math.max(0,Math.min(Settings.get().maxWirelessRange[1],args.checkDouble(0)));if(range<=0)return new Object[]{new Object[0]};if(!connector().tryChangeBuffer(-range*Settings.get().wirelessCostPerRange[1]*.25))return new Object[]{null,"not enough energy"};context.pause(.5);Vec3 here=new Vec3(host.xPosition(),host.yPosition(),host.zPosition());List<Map<String,Object>> values=new ArrayList<>();for(var waypoint:Waypoints.findWaypoints(host.world(),position(),range)){if(Vec3.atCenterOf(waypoint.getBlockPos()).distanceToSqr(here)>range*range)continue;Vec3 delta=Vec3.atCenterOf(waypoint.getBlockPos().relative(waypoint.facing())).subtract(here);values.add(Map.of("position",new double[]{delta.x,delta.y,delta.z},"redstone",waypoint.maxInput(),"label",waypoint.label(),"address",waypoint.node().address()));}return new Object[]{values.toArray()};}
    @Override public void onMessage(Message message){if(message.name().equals("tablet.use")&&message.data().length>=4&&message.data()[0] instanceof CompoundTag result&&message.data()[3] instanceof BlockPos pos){var map=data.mapData(host.world());result.putInt("posX",pos.getX()-map.centerX);result.putInt("posY",pos.getY());result.putInt("posZ",pos.getZ()-map.centerZ);}}
    @Override public void load(CompoundTag tag,HolderLookup.Provider registries){super.load(tag);data.load(tag,registries);}
    @Override public void save(CompoundTag tag,HolderLookup.Provider registries){super.save(tag);data.save(tag,registries);}
}
