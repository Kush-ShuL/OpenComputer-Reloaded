package li.cil.oc.common.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Original common block-item book-enchantment restriction for adapters, cases and racks. */
public final class MachineHostBlockItem extends BlockItem {
    public MachineHostBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book){return false;}
}
