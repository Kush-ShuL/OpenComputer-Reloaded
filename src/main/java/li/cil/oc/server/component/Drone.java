package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.machine.*;
import li.cil.oc.server.agent.AgentHost;
import li.cil.oc.common.entity.DroneEntity;
import net.minecraft.world.phys.Vec3;

public final class Drone extends AgentComponent {
    public Drone(AgentHost host){super(host,"drone",Settings.get().bufferDrone);}
    private DroneEntity drone(){return (DroneEntity)agent;}
    @Callback public Object[] getStatusText(Context context,Arguments args){return new Object[]{drone().statusText()};}
    @Callback public Object[] setStatusText(Context context,Arguments args){drone().setStatusText(args.checkString(0));context.pause(.1);return new Object[]{drone().statusText()};}
    @Callback public Object[] getLightColor(Context context,Arguments args){return new Object[]{drone().lightColor()};}
    @Callback public Object[] setLightColor(Context context,Arguments args){drone().setLightColor(args.checkInteger(0)&0xFFFFFF);context.pause(.1);return new Object[]{drone().lightColor()};}
    @Callback public Object[] move(Context context,Arguments args){drone().setTarget(drone().getTarget().add((float)args.checkDouble(0),(float)args.checkDouble(1),(float)args.checkDouble(2)));return null;}
    @Callback public Object[] getOffset(Context context,Arguments args){return new Object[]{drone().position().distanceTo(drone().getTarget())};}
    @Callback public Object[] getVelocity(Context context,Arguments args){return new Object[]{drone().getVelocity().length()*20};}
    @Callback public Object[] getMaxVelocity(Context context,Arguments args){return new Object[]{8};}
    @Callback public Object[] getAcceleration(Context context,Arguments args){return new Object[]{drone().acceleration()*20};}
    @Callback public Object[] setAcceleration(Context context,Arguments args){drone().setAcceleration((float)(args.checkDouble(0)/20));return new Object[]{drone().acceleration()*20};}
}
