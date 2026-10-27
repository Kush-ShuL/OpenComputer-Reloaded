package li.cil.oc.common.item;

import java.util.List;
import java.util.Objects;
import li.cil.oc.OpenComputers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Original manual item: keep page history, sneak to reset, or open a block's page. */
public final class ManualItem extends Item {
    /** Installed only by the physical client; no GUI classes are linked here. */
    public interface ClientActions {
        void open(Player player, boolean reset);
        boolean openForBlock(Player player, BlockPos position);
    }

    private static volatile ClientActions clientActions;

    public ManualItem(Properties properties) { super(properties); }
    @Override public boolean isBookEnchantable(ItemStack stack, ItemStack book) { return false; }
    @Override public int getMaxStackSize(ItemStack stack) { return PlayerItemTooltips.stackSize(stack, super.getMaxStackSize(stack)); }

    public static void setClientActions(ClientActions actions) {
        clientActions = Objects.requireNonNull(actions);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ClientActions actions = clientActions;
        if (level.isClientSide && actions != null) actions.open(player, player.isShiftKeyDown());
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ClientActions actions = clientActions;
        if (context.getLevel().isClientSide && player != null && actions != null
                && actions.openForBlock(player, context.getClickedPos())) return InteractionResult.SUCCESS;
        return super.useOn(context);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("v" + OpenComputers.VERSION).withStyle(ChatFormatting.DARK_GRAY));
        PlayerItemTooltips.append(stack, tooltip, "oc:tooltip.manual");
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
