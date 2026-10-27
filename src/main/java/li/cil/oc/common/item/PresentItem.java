package li.cil.oc.common.item;

import java.util.*;
import li.cil.oc.api.Items;
import li.cil.oc.util.InventoryUtils;
import li.cil.oc.util.ItemUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

/** Original weighted seasonal presents, restricted to enabled crafting outputs. */
public final class PresentItem extends Item {
    private static final Random RANDOM = new Random();
    private static final String[] NAMES = {
        "arrowkeys","buttongroup","numpad","disk","transistor","floppy","printedcircuitboard","chip1","eeprom","interweb","card","analyzer","signupgrade","inventoryupgrade","craftingupgrade","tankupgrade","pistonupgrade","leashupgrade","angelupgrade","redstonecard1","ram1","cu","alu","batteryupgrade1","lancard","wlancard1","hdd1","generatorupgrade","cpu1","microcontrollercase1","dronecase1","upgradecontainer1","cardcontainer1","graphicscard1","redstonecard2","ram2","databaseupgrade1","chip2","componentbus1","batteryupgrade2","wlancard2","ram3","server1","internetcard","terminal","solargeneratorupgrade","hdd2","navigationupgrade","inventorycontrollerupgrade","tankcontrollerupgrade","cpu2","microcontrollercase2","componentbus2","tabletcase1","upgradecontainer2","cardcontainer2","graphicscard2","ram4","dronecase2","databaseupgrade2","server2","chip3","componentbus3","tractorbeamupgrade","batteryupgrade3","experienceupgrade","ram5","upgradecontainer3","cardcontainer3","tabletcase2","hdd3","chunkloaderupgrade","cpu3","graphicscard3","server3","databaseupgrade3","ram6"
    };
    private static final int[] WEIGHTS = {
        520,460,410,370,350,340,320,290,250,220,190,170,150,130,110,90,80,70,55,50,48,46,45,43,38,37,36,35,31,30,25,23,23,19,17,15,15,15,13,12,11,10,10,9,9,9,7,7,7,7,6,6,6,5,5,5,4,4,4,4,4,3,3,3,3,2,2,2,2,1,1,1,1,1,1,1,1
    };
    public PresentItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        for (String line : Component.translatable("oc:tooltip.present").getString().split("\\[nl\\]")) tooltip.add(Component.literal(line));
    }
    public static ItemStack nextPresent(Level level) {
        var candidates = new ArrayList<ItemStack>();
        var weights = new ArrayList<Integer>();
        int total = 0;
        for (int i = 0; i < NAMES.length; i++) {
            var descriptor = Items.get(NAMES[i]);
            if (descriptor == null) continue;
            ItemStack candidate = descriptor.createItemStack(1);
            if (!candidate.isEmpty() && ItemUtils.getIngredients(candidate, level).length > 0) {
                candidates.add(candidate); weights.add(WEIGHTS[i]); total += WEIGHTS[i];
            }
        }
        if (total == 0) return ItemStack.EMPTY;
        int choice = RANDOM.nextInt(total);
        for (int i = 0; i < candidates.size(); i++) {
            choice -= weights.get(i);
            if (choice < 0) return candidates.get(i).copy();
        }
        throw new IllegalStateException("Invalid present weight total");
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty()) {
            stack.shrink(1);
            if (!level.isClientSide) {
                level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, .2f, 1);
                InventoryUtils.addToPlayerInventory(nextPresent(level), player, true);
            }
        }
        return InteractionResultHolder.success(stack);
    }
}
