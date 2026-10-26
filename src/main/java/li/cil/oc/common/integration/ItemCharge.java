package li.cil.oc.common.integration;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/** Ordered item charging hooks. A charge callback returns the unapplied remainder. */
public final class ItemCharge {
    @FunctionalInterface public interface Charge { double charge(ItemStack stack, double amount, boolean simulate); }
    public record Handler(String name, Predicate<ItemStack> canCharge, Charge charge) { public Handler { java.util.Objects.requireNonNull(name); java.util.Objects.requireNonNull(canCharge); java.util.Objects.requireNonNull(charge); } }
    private static final CopyOnWriteArrayList<Handler> HANDLERS = new CopyOnWriteArrayList<>();
    private ItemCharge() {}
    public static void add(Handler handler) { HANDLERS.addIfAbsent(java.util.Objects.requireNonNull(handler)); }
    public static void add(String name, Method canCharge, Method charge) { add(new Handler(name, stack -> CallbackMethods.booleanResult(canCharge, false, stack), (stack, amount, simulate) -> CallbackMethods.doubleResult(charge, amount, stack, amount, simulate))); }
    public static List<Handler> handlers() { return List.copyOf(HANDLERS); }
    public static boolean canCharge(ItemStack stack) { return stack != null && !stack.isEmpty() && HANDLERS.stream().anyMatch(handler -> handler.canCharge.test(stack)); }
    public static double charge(ItemStack stack, double amount) { return charge(stack, amount, false); }
    public static double charge(ItemStack stack, double amount, boolean simulate) {
        if (stack != null && !stack.isEmpty()) for (Handler handler : HANDLERS) if (handler.canCharge.test(stack)) return handler.charge.charge(stack, amount, simulate);
        return amount;
    }
}
