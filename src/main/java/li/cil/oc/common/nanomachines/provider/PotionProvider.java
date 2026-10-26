package li.cil.oc.common.nanomachines.provider;
import li.cil.oc.Settings;
import li.cil.oc.api.nanomachines.*;
import li.cil.oc.api.prefab.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.player.Player;
import java.util.*;
public final class PotionProvider extends AbstractProvider {
    public static final PotionProvider INSTANCE=new PotionProvider();
    private PotionProvider(){super("c29e4eec-5a46-479a-9b3d-ad0f06da784a");}
    public Iterable<Behavior> createBehaviors(Player player){List<Behavior> result=new ArrayList<>();for(String name:Settings.get().nanomachinePotionWhitelist){ResourceLocation id=ResourceLocation.tryParse(name);if(id!=null)BuiltInRegistries.MOB_EFFECT.getHolder(id).ifPresent(effect->result.add(new PotionBehavior(effect,player)));}return result;}
    protected void writeBehaviorToNBT(Behavior behavior,CompoundTag nbt){nbt.putString("potionId",BuiltInRegistries.MOB_EFFECT.getKey(((PotionBehavior)behavior).effect.value()).toString());}
    protected Behavior readBehaviorFromNBT(Player player,CompoundTag nbt){ResourceLocation id=ResourceLocation.tryParse(nbt.getString("potionId"));return id==null?null:BuiltInRegistries.MOB_EFFECT.getHolder(id).map(effect->(Behavior)new PotionBehavior(effect,player)).orElse(null);}
    public static final class PotionBehavior extends AbstractBehavior {
        final Holder<MobEffect> effect;PotionBehavior(Holder<MobEffect> effect,Player player){super(player);this.effect=effect;}
        public String getNameHint(){String name=effect.value().getDescriptionId();return name.startsWith("effect.minecraft.")?name.substring(17):name;}
        public void onDisable(DisableReason reason){player.removeEffect(effect);}
        public void update(){if(!player.level().isClientSide){Controller controller=li.cil.oc.api.Nanomachines.getController(player);if(controller!=null)player.addEffect(new MobEffectInstance(effect,600,Math.max(0,controller.getInputCount(this)-1),true,Settings.get().enableNanomachinePfx));}}
    }
}
