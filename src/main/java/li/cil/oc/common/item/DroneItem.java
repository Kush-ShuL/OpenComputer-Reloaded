package li.cil.oc.common.item;

import li.cil.oc.common.init.Agents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public final class DroneItem extends Item {
    public DroneItem(Properties properties){super(properties);}
    @Override public boolean isBookEnchantable(net.minecraft.world.item.ItemStack stack,net.minecraft.world.item.ItemStack book){return false;}
    @Override public int getMaxStackSize(net.minecraft.world.item.ItemStack stack){return PlayerItemTooltips.stackSize(stack,super.getMaxStackSize(stack));}
    @Override public void verifyComponentsAfterLoad(net.minecraft.world.item.ItemStack stack){stack.set(net.minecraft.core.component.DataComponents.RARITY,PlayerItemTooltips.rarity(li.cil.oc.common.item.data.ItemData.customData(stack).getInt("oc:tier")));}
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,TooltipContext context,java.util.List<net.minecraft.network.chat.Component> tooltip,net.minecraft.world.item.TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);PlayerItemTooltips.append(stack,tooltip,"oc:tooltip.drone");
        if(RobotDroneTooltips.extended()&&context.registries()!=null)RobotDroneTooltips.components(new li.cil.oc.common.item.data.DroneData(stack,context.registries()).components,tooltip);
    }
    @Override public InteractionResult useOn(UseOnContext context){if(context.getPlayer()==null)return InteractionResult.PASS;var level=context.getLevel();if(!level.isClientSide){var drone=Agents.DRONE_TYPE.get().create(level);if(drone==null)return InteractionResult.FAIL;Vec3 hit=context.getClickLocation().subtract(Vec3.atLowerCornerOf(context.getClickedPos()));Vec3 pos=Vec3.atLowerCornerOf(context.getClickedPos()).add(hit.scale(1.1));drone.initializeAfterPlacement(context.getItemInHand(),context.getPlayer(),pos);if(!level.addFreshEntity(drone)){drone.discard();return InteractionResult.FAIL;}if(!context.getPlayer().getAbilities().instabuild)context.getItemInHand().shrink(1);}return InteractionResult.sidedSuccess(level.isClientSide);}
}
