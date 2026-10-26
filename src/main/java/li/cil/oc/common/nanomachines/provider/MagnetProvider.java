package li.cil.oc.common.nanomachines.provider;
import li.cil.oc.Settings;
import li.cil.oc.api.nanomachines.*;
import li.cil.oc.api.prefab.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.List;
public final class MagnetProvider extends AbstractProvider {
    public static final MagnetProvider INSTANCE=new MagnetProvider();
    private MagnetProvider(){super("9324d5ec-71f1-41c2-b51c-406e527668fc");}
    public Iterable<Behavior> createBehaviors(Player player){return List.of(new MagnetBehavior(player));}
    protected Behavior readBehaviorFromNBT(Player player,CompoundTag nbt){return new MagnetBehavior(player);}
    public static final class MagnetBehavior extends AbstractBehavior {MagnetBehavior(Player player){super(player);}public String getNameHint(){return"magnet";}public void update(){if(player.level().isClientSide)return;Controller controller=li.cil.oc.api.Nanomachines.getController(player);if(controller==null)return;double range=Settings.get().nanomachineMagnetRange*controller.getInputCount(this);for(ItemEntity item:player.level().getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(range))){if(item.hasPickUpDelay()||item.getItem().isEmpty())continue;boolean room=player.getInventory().items.stream().anyMatch(stack->stack.isEmpty()||stack.getCount()<stack.getMaxStackSize()&&ItemStack.isSameItem(stack,item.getItem()));if(room){var velocity=player.position().subtract(item.position()).normalize().scale(.1);item.push(velocity.x,velocity.y,velocity.z);}}}}
}
