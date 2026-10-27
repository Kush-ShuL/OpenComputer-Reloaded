package li.cil.oc.common.integration.enderio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Original EnderIO wrench callbacks adapted to the official 1.21.1 tag contract. */
public final class EventHandlerEnderIO {
    private static final ResourceLocation YETA_WRENCH = ResourceLocation.fromNamespaceAndPath("enderio", "yeta_wrench");
    private static final TagKey<Item> WRENCH = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));
    private EventHandlerEnderIO() {}

    public static boolean isWrench(ItemStack stack) {
        return EnderIOIntegration.available() && stack != null && !stack.isEmpty()
            && YETA_WRENCH.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())) && stack.is(WRENCH);
    }

    public static boolean useWrench(Player player, BlockPos position, boolean changeDurability) {
        // Official 8.2.12 Yeta has no ITool/canUse/used hook, damage or charge.
        // Its registry item and common wrench tag are the public modern contract.
        // Calling onItemUseFirst here would perform a second, unrelated block action.
        return player != null && isWrench(player.getMainHandItem());
    }
}
