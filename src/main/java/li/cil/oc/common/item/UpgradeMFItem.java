package li.cil.oc.common.item;

import java.util.List;
import li.cil.oc.api.internal.Tiered;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Original MFU: sneak-use first to remember one block, dimension and face. */
public final class UpgradeMFItem extends Item implements Tiered {
    public UpgradeMFItem(Properties properties) { super(properties); }
    @Override public int tier() { return 2; }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            BlockPos position = context.getClickedPos(); ResourceKey<Level> dimension = context.getLevel().dimension();
            int legacy = dimension.equals(Level.OVERWORLD) ? 0 : dimension.equals(Level.NETHER) ? -1 : dimension.equals(Level.END) ? 1 : Integer.MIN_VALUE;
            CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> { root.putIntArray("oc:coord", new int[]{position.getX(), position.getY(), position.getZ(), legacy, context.getClickedFace().get3DDataValue()}); root.putString("oc:dimension", dimension.location().toString()); });
            player.getInventory().setChanged();
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    public static ResourceKey<Level> dimension(CompoundTag root) {
        ResourceLocation id = ResourceLocation.tryParse(root.getString("oc:dimension"));
        if (id != null) return ResourceKey.create(Registries.DIMENSION, id);
        int[] coordinates = root.getIntArray("oc:coord");
        if (coordinates.length != 5) return null;
        return switch (coordinates[3]) { case -1 -> Level.NETHER; case 0 -> Level.OVERWORLD; case 1 -> Level.END; default -> null; };
    }
    @Override public int getMaxStackSize(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("oc:data").getCompound("node").contains("address") ? 1 : super.getMaxStackSize(stack); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("oc:tooltip.upgrademf"));
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tooltip.add(Component.translatable(root.getIntArray("oc:coord").length == 5 && dimension(root) != null ? "oc:tooltip.upgrademf.Linked" : "oc:tooltip.upgrademf.Unlinked"));
        if (flag.isAdvanced()) tooltip.add(Component.translatable("oc:tooltip.tier", 3));
    }
}
