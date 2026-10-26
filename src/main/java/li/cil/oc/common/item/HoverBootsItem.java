package li.cil.oc.common.item;

import java.util.List;
import java.util.function.Consumer;
import li.cil.oc.Settings;
import li.cil.oc.common.item.data.HoverBootsData;
import li.cil.oc.util.ItemColorizer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;

/** Diamond armor whose damage and movement costs consume its original energy store. */
public final class HoverBootsItem extends ArmorItem implements li.cil.oc.api.driver.item.Chargeable {
    public HoverBootsItem(Properties properties) {
        super(ArmorMaterials.DIAMOND, ArmorItem.Type.BOOTS, properties.stacksTo(1).rarity(Rarity.UNCOMMON)
                .durability((int) Math.min(Integer.MAX_VALUE, Settings.get().bufferHoverBoots)));
    }
    public double maxCharge(ItemStack stack) { return Settings.get().bufferHoverBoots; }
    public double getCharge(ItemStack stack) {
        double value = new HoverBootsData(stack).charge;
        return Double.isFinite(value) ? Math.clamp(value, 0, maxCharge(stack)) : 0;
    }
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
            if (!simulate && used != 0) setCharge(stack, target);
            return amount - used;
        }
    }
    @Override public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
        charge(stack, -Math.max(0, amount), false);
        return 0;
    }
    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) { return false; }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return (int) Math.round(13 * getCharge(stack) / maxCharge(stack)); }
    @Override public int getBarColor(ItemStack stack) { return 0x66dd55; }
    @Override public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean inner) {
        return ResourceLocation.parse("opencomputers:textures/model/drone.png");
    }
    @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide && ItemColorizer.hasColor(stack)) {
            var pos = entity.blockPosition();
            var state = entity.level().getBlockState(pos);
            if (state.is(Blocks.WATER_CAULDRON)) {
                ItemColorizer.removeColor(stack);
                LayeredCauldronBlock.lowerFillLevel(state, entity.level(), pos);
                entity.setItem(stack);
            }
        }
        return false;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("oc:tooltip.hoverboots"));
    }
}
