package li.cil.oc.common;

import java.lang.reflect.Method;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;
import li.cil.oc.common.integration.CallbackMethods;
import net.minecraft.world.item.ItemStack;

/** First non-NaN provider wins, followed by vanilla damage as in the original. */
public final class ToolDurabilityProviders {
    private static final CopyOnWriteArrayList<ToDoubleFunction<ItemStack>> PROVIDERS = new CopyOnWriteArrayList<>();
    private ToolDurabilityProviders() {}
    public static void add(ToDoubleFunction<ItemStack> provider) { PROVIDERS.addIfAbsent(java.util.Objects.requireNonNull(provider)); }
    public static void add(Method method) { add(stack -> CallbackMethods.doubleResult(method, Double.NaN, stack)); }
    public static List<ToDoubleFunction<ItemStack>> providers() { return List.copyOf(PROVIDERS); }
    public static OptionalDouble getDurability(ItemStack stack) {
        for (ToDoubleFunction<ItemStack> provider : PROVIDERS) { double durability = provider.applyAsDouble(stack); if (!Double.isNaN(durability)) return OptionalDouble.of(durability); }
        return stack != null && stack.isDamageableItem() && stack.getMaxDamage() > 0 ? OptionalDouble.of(1d - (double) stack.getDamageValue() / stack.getMaxDamage()) : OptionalDouble.empty();
    }
}
