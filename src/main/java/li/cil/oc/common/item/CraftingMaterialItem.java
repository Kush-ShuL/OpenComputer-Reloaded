package li.cil.oc.common.item;

import li.cil.oc.Settings;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Crafting materials with the original grog, chamelium and cartridge behavior. */
public final class CraftingMaterialItem extends Item {
    public enum Kind { PLAIN, ACID, CHAMELIUM, INK }
    private final Kind kind;
    public CraftingMaterialItem(Properties properties, Kind kind) { super(properties);this.kind=kind; }
    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return kind==Kind.INK||super.hasCraftingRemainingItem(stack); }
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack) {
        if(kind!=Kind.INK)return super.getCraftingRemainingItem(stack);
        var empty=li.cil.oc.api.Items.get("inkcartridgeempty");
        if(empty==null)throw new IllegalStateException("Empty ink cartridge is not registered");
        return empty.createItemStack(1);
    }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return kind==Kind.ACID?UseAnim.DRINK:kind==Kind.CHAMELIUM?UseAnim.EAT:super.getUseAnimation(stack); }
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity) { return kind==Kind.ACID||kind==Kind.CHAMELIUM?32:super.getUseDuration(stack,entity); }
    @Override public InteractionResultHolder<ItemStack> use(Level world,Player player,InteractionHand hand) {
        if(kind==Kind.ACID||kind==Kind.CHAMELIUM&&Settings.get().chameliumEdible){player.startUsingItem(hand);return InteractionResultHolder.consume(player.getItemInHand(hand));}
        return super.use(world,player,hand);
    }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level world,LivingEntity entity) {
        if(kind==Kind.ACID&&entity instanceof Player player){
            if(!world.isClientSide){
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,200));player.addEffect(new MobEffectInstance(MobEffects.POISON,100));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,600));player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,1200));
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION,2000));li.cil.oc.api.Nanomachines.uninstallController(player);
            }
            stack.shrink(1);return stack.isEmpty()?ItemStack.EMPTY:stack;
        }
        if(kind==Kind.CHAMELIUM){if(!world.isClientSide){entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,100));entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,200));}stack.shrink(1);return stack.isEmpty()?ItemStack.EMPTY:stack;}
        return super.finishUsingItem(stack,world,entity);
    }
}
