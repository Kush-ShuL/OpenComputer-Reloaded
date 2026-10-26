package li.cil.oc.common.integration;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Wrench checks and actual usage hooks, including registered third-party tools. */
public final class WrenchTools {
    @FunctionalInterface public interface Usage { boolean use(Player player, BlockPos position, boolean changeDurability); }
    private static final CopyOnWriteArrayList<Predicate<ItemStack>> CHECKS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Usage> USAGES = new CopyOnWriteArrayList<>();
    private static final TagKey<Item> WRENCHES = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/wrench"));
    private WrenchTools() {}
    public static void addCheck(Predicate<ItemStack> check) { CHECKS.addIfAbsent(java.util.Objects.requireNonNull(check)); }
    public static void addUsage(Usage usage) { USAGES.addIfAbsent(java.util.Objects.requireNonNull(usage)); }
    public static void addCheck(Method method) { addCheck(stack -> CallbackMethods.booleanResult(method, false, stack)); }
    public static void addUsage(Method method) { addUsage((player, pos, change) -> CallbackMethods.booleanResult(method, false, player, pos, change)); }
    public static List<Predicate<ItemStack>> checks() { return List.copyOf(CHECKS); }
    public static List<Usage> usages() { return List.copyOf(USAGES); }
    public static boolean isWrench(ItemStack stack) { return stack != null && !stack.isEmpty() && (stack.getItem() instanceof li.cil.oc.api.internal.Wrench || stack.is(WRENCHES) || CHECKS.stream().anyMatch(check -> check.test(stack))); }
    public static boolean holdsApplicableWrench(Player player, BlockPos pos) { return use(player, pos, false); }
    public static boolean use(Player player, BlockPos pos, boolean changeDurability) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof li.cil.oc.api.internal.Wrench wrench && wrench.useWrenchOnBlock(player, player.level(), pos, !changeDurability)) return true;
        boolean used = false;
        for (Usage usage : USAGES) if (usage.use(player, pos, changeDurability)) { used = true; if (!changeDurability) break; }
        return used;
    }
    public static void wrenchUsed(Player player, BlockPos pos) { use(player, pos, true); }
}
