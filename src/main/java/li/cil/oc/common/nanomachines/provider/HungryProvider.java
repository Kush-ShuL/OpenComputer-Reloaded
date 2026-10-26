package li.cil.oc.common.nanomachines.provider;
import li.cil.oc.Settings;
import li.cil.oc.api.nanomachines.*;
import li.cil.oc.api.prefab.*;
import li.cil.oc.common.nanomachines.NanoDamage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import java.util.*;
public final class HungryProvider extends AbstractProvider {
    public static final HungryProvider INSTANCE=new HungryProvider();
    private HungryProvider(){super("d697c24a-014c-4773-a288-23084a59e9e8");}
    public Iterable<Behavior> createBehaviors(Player player){List<Behavior> result=new ArrayList<>();for(int i=0;i<10;i++)result.add(new HungryBehavior(player));return result;}
    protected Behavior readBehaviorFromNBT(Player player,CompoundTag nbt){return new HungryBehavior(player);}
    public static final class HungryBehavior extends AbstractBehavior {HungryBehavior(Player player){super(player);}public void onDisable(DisableReason reason){if(reason==DisableReason.OutOfEnergy&&!player.level().isClientSide){player.hurt(NanoDamage.hungry(player),Settings.get().nanomachinesHungryDamage);Controller controller=li.cil.oc.api.Nanomachines.getController(player);if(controller!=null)controller.changeBuffer(Settings.get().nanomachinesHungryEnergyRestored);}}}
}
