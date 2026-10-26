package li.cil.oc.common.item;

import java.util.List;
import li.cil.oc.common.item.data.PrintData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public final class PrintBlockItem extends BlockItem {
    public PrintBlockItem(Block block, Properties properties) { super(block, properties); }
    @Override public Component getName(ItemStack stack) { return new PrintData(stack).label.<Component>map(Component::literal).orElseGet(() -> super.getName(stack)); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag); PrintData data = new PrintData(stack);
        data.tooltip.ifPresent(text -> text.lines().map(Component::literal).forEach(tooltip::add));
        if (data.isBeaconBase) tooltip.add(Component.translatable("oc:tooltip.print.BeaconBase"));
        if (data.emitRedstone()) tooltip.add(Component.translatable("oc:tooltip.print.RedstoneLevel", data.redstoneLevel));
        if (data.emitLight()) tooltip.add(Component.translatable("oc:tooltip.print.LightValue", data.lightLevel));
    }
}
