package li.cil.oc.common.item;

import li.cil.oc.common.init.Agents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public final class DroneItem extends Item {
    public DroneItem(Properties properties){super(properties.stacksTo(1));}
    @Override public InteractionResult useOn(UseOnContext context){if(context.getPlayer()==null)return InteractionResult.PASS;var level=context.getLevel();if(!level.isClientSide){var drone=Agents.DRONE_TYPE.get().create(level);if(drone==null)return InteractionResult.FAIL;Vec3 pos=Vec3.atCenterOf(context.getClickedPos().relative(context.getClickedFace()));drone.initializeAfterPlacement(context.getItemInHand(),context.getPlayer(),pos);if(!level.noCollision(drone,drone.getBoundingBox())||!level.addFreshEntity(drone))return InteractionResult.FAIL;if(!context.getPlayer().getAbilities().instabuild)context.getItemInHand().shrink(1);}return InteractionResult.sidedSuccess(level.isClientSide);}
}
