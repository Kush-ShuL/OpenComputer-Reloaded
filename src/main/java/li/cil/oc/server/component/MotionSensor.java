package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/** Living-entity motion tracking, line-of-sight filtering and motion signals. */
public final class MotionSensor extends AbstractManagedEnvironment implements DeviceInfo {
    public final EnvironmentHost host;
    private double sensitivity=.4;
    private final Map<LivingEntity,Vec3> tracked=new IdentityHashMap<>();
    public MotionSensor(EnvironmentHost host){this.host=host;setNode(li.cil.oc.api.Network.newNode(this,Visibility.Network).withComponent("motion_sensor").withConnector().create());}
    @Override public Map<String,String> getDeviceInfo(){return Map.of("class","generic","description","Motion sensor","vendor","MightyPirates GmbH & Co. KG","product","Blinker M1K0","capacity","8");}
    @Override public boolean canUpdate(){return host.world()!=null&&!host.world().isClientSide;}
    private Vec3 origin(){return new Vec3(host.xPosition(),host.yPosition(),host.zPosition());}
    private boolean clear(Vec3 target){Vec3 origin=origin(),eye=origin.add(target.subtract(origin).normalize());return host.world().clip(new ClipContext(eye,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty())).getType()==HitResult.Type.MISS;}
    @Override public void update(){
        if(!canUpdate()||host.world().getGameTime()%10!=0)return;Vec3 origin=origin();Set<LivingEntity> visible=Collections.newSetFromMap(new IdentityHashMap<>());
        for(LivingEntity entity:host.world().getEntitiesOfClass(LivingEntity.class,new AABB(origin,origin).inflate(8),entity->entity.isAlive()&&!entity.hasEffect(MobEffects.INVISIBILITY)&&entity.distanceToSqr(origin)<=64)){
            if(!clear(entity.position())&&!clear(entity.getEyePosition()))continue;visible.add(entity);Vec3 previous=tracked.put(entity,entity.position());
            if(previous==null||entity.distanceToSqr(previous)>sensitivity*sensitivity*2){Vec3 offset=entity.position().subtract(origin);if(Settings.get().inputUsername)node().sendToReachable("computer.signal","motion",offset.x,offset.y,offset.z,entity.getName().getString());else node().sendToReachable("computer.signal","motion",offset.x,offset.y,offset.z);}
        }tracked.keySet().retainAll(visible);
    }
    @Callback(direct=true,doc="function():number -- Get sensor sensitivity.") public Object[] getSensitivity(Context context,Arguments args){return new Object[]{sensitivity};}
    @Callback(direct=true,doc="function(value:number):number -- Set sensor sensitivity and return its old value.") public Object[] setSensitivity(Context context,Arguments args){double value=args.checkDouble(0);if(!Double.isFinite(value))throw new IllegalArgumentException("invalid sensitivity");double old=sensitivity;sensitivity=Math.max(.2,value);host.markChanged();return new Object[]{old};}
    @Override public void load(CompoundTag tag){super.load(tag);if(tag.contains("oc:sensitivity"))sensitivity=Math.max(.2,tag.getDouble("oc:sensitivity"));}
    @Override public void save(CompoundTag tag){super.save(tag);tag.putDouble("oc:sensitivity",sensitivity);}
}
