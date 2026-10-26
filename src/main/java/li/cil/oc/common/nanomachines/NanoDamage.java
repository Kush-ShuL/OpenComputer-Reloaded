package li.cil.oc.common.nanomachines;
import li.cil.oc.OpenComputers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
public final class NanoDamage {
    private static DamageSource source(Player player,String type,String message){var key=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath(OpenComputers.ID,type));return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key)){public Component getLocalizedDeathMessage(LivingEntity target){return Component.translatable("death.attack.oc."+message+"."+(1+target.getRandom().nextInt(3)),target.getDisplayName());}};}
    public static DamageSource overload(Player player){return source(player,"nanomachines_overload","nanomachinesOverload");}
    public static DamageSource hungry(Player player){return source(player,"nanomachines_hungry","nanomachinesHungry");}
}
