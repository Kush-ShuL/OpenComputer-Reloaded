package li.cil.oc.common.item;

import java.util.List;
import java.util.function.Consumer;
import li.cil.oc.Settings;
import li.cil.oc.common.item.data.HoverBootsData;
import li.cil.oc.util.ItemColorizer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Diamond armor whose damage and movement costs consume its original energy store. */
public final class HoverBootsItem extends ArmorItem implements li.cil.oc.api.driver.item.Chargeable {
    public HoverBootsItem(Properties properties) {
        super(ArmorMaterials.DIAMOND, ArmorItem.Type.BOOTS, properties.stacksTo(1).rarity(Rarity.UNCOMMON)
                .durability((int) Math.min(Integer.MAX_VALUE, Settings.get().bufferHoverBoots)));
    }
    public double maxCharge(ItemStack stack) { return Settings.get().bufferHoverBoots; }
    public double getCharge(ItemStack stack) { return new HoverBootsData(stack).charge; }
    public void setCharge(ItemStack stack, double value) {
        if (Double.isNaN(value)) throw new IllegalArgumentException("charge cannot be NaN");
        var tag = li.cil.oc.util.ItemUtils.customData(stack);
        var data = new HoverBootsData();
        data.charge = Math.clamp(value, 0, maxCharge(stack));
        data.save(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    @Override public boolean canCharge(ItemStack stack) { return true; }
    @Override public double charge(ItemStack stack, double amount, boolean simulate) {
        if (Double.isNaN(amount)) throw new IllegalArgumentException("charge cannot be NaN");
        synchronized (stack) {
            double current = getCharge(stack), target = Math.clamp(current + amount, 0, maxCharge(stack));
            double used = target - current;
            if (!simulate && (used > Double.MIN_VALUE || used < -Double.MIN_VALUE)) setCharge(stack, target);
            return amount - used;
        }
    }
    /** Original Chargeable.Provider FE conversion: report used input from the unused OC remainder. */
    public IEnergyStorage energyStorage(ItemStack stack) {
        return new IEnergyStorage() {
            private double ratio() { return Settings.get().ratioForgeEnergy; }
            @Override public int receiveEnergy(int amount, boolean simulate) {
                if (amount <= 0 || ratio() <= 0) return 0;
                synchronized (stack) {
                    double unused = charge(stack, amount / ratio(), simulate);
                    return amount - (int) (unused * ratio());
                }
            }
            @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
            @Override public int getEnergyStored() { return (int) (getCharge(stack) * ratio()); }
            @Override public int getMaxEnergyStored() { return (int) (maxCharge(stack) * ratio()); }
            @Override public boolean canExtract() { return false; }
            @Override public boolean canReceive() { return canCharge(stack); }
        };
    }
    @Override public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
        if (amount > 0 && (entity == null || !entity.hasInfiniteMaterials())) {
            // This hook precedes vanilla's enchantment processing. Preserve Unbreaking's
            // original reduction, then stop vanilla from applying damage/breaking the boots.
            int damage = entity != null && entity.level() instanceof ServerLevel level
                    ? EnchantmentHelper.processDurabilityChange(level, stack, amount) : amount;
            if (damage > 0) setDamage(stack, damage);
        }
        return 0;
    }
    @Override public int getMaxDamage(ItemStack stack) { return (int) maxCharge(stack); }
    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public boolean isDamaged(ItemStack stack) { return true; }
    @Override public void setDamage(ItemStack stack, int damage) {
        charge(stack, -damage, false);
        stack.set(DataComponents.DAMAGE, 0);
    }
    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) { return false; }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return (int) Math.round(13 * getCharge(stack) / maxCharge(stack)); }
    @Override public int getBarColor(ItemStack stack) { return Mth.hsvToRgb(Math.max(0, (float) (getCharge(stack) / maxCharge(stack))) / 3, 1, 1); }
    @Override public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean inner) {
        return ResourceLocation.parse("opencomputers:textures/model/drone.png");
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        int legacyDamage = stack.getOrDefault(DataComponents.DAMAGE, 0);
        if (legacyDamage != 0) setDamage(stack, legacyDamage);
        if (entity instanceof Player player && !Settings.get().ignorePower && getCharge(stack) == 0
                && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            for (ItemStack equipped : player.getArmorSlots()) {
                if (equipped == stack) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
                    break;
                }
            }
        }
    }
    @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide && ItemColorizer.hasColor(stack)) {
            var pos = entity.blockPosition();
            var state = entity.level().getBlockState(pos);
            if (state.is(Blocks.WATER_CAULDRON)) {
                ItemColorizer.removeColor(stack);
                LayeredCauldronBlock.lowerFillLevel(state, entity.level(), pos);
                entity.setItem(stack);
                return true;
            }
        }
        return false;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("oc:tooltip.hoverboots"));
    }
}
