package li.cil.oc.common.template;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import li.cil.oc.api.Driver;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.common.Tier;
import li.cil.oc.common.integration.CallbackMethods;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Retained, callable assembly templates; device implementation consumes this registry. */
public final class AssemblerTemplates {
    private static final CopyOnWriteArrayList<Template> TEMPLATES = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Predicate<ItemStack>> FILTERS = new CopyOnWriteArrayList<>();
    public static final Slot NO_SLOT = new Slot(li.cil.oc.api.driver.item.Slot.None, Tier.None, null, null);
    private AssemblerTemplates() {}
    public static void add(CompoundTag data) throws ReflectiveOperationException {
        Method select = CallbackMethods.resolve(data.getString("select"), boolean.class, new Class<?>[]{ItemStack.class});
        Method validate = CallbackMethods.resolve(data.getString("validate"), Object[].class, new Class<?>[]{Container.class});
        Method assemble = CallbackMethods.resolve(data.getString("assemble"), Object[].class, new Class<?>[]{Container.class});
        Class<? extends EnvironmentHost> host = data.getString("hostClass").isEmpty() ? null : Class.forName(data.getString("hostClass")).asSubclass(EnvironmentHost.class);
        TEMPLATES.add(new Template(data.getString("name"), select, validate, assemble, slots(data, "containerSlots", li.cil.oc.api.driver.item.Slot.Container, 3, host), slots(data, "upgradeSlots", li.cil.oc.api.driver.item.Slot.Upgrade, 9, host), slots(data, "componentSlots", null, 9, host)));
    }
    public static void addFilter(String name) throws ReflectiveOperationException { Method method = CallbackMethods.resolve(name, boolean.class, new Class<?>[]{ItemStack.class}); addFilter(stack -> CallbackMethods.booleanResult(method, true, stack)); }
    public static void addFilter(Predicate<ItemStack> filter) { FILTERS.addIfAbsent(java.util.Objects.requireNonNull(filter)); }
    public static List<Template> templates() { return List.copyOf(TEMPLATES); }
    public static List<Predicate<ItemStack>> filters() { return List.copyOf(FILTERS); }
    public static Optional<Template> select(ItemStack stack) { return stack != null && !stack.isEmpty() && FILTERS.stream().allMatch(filter -> filter.test(stack)) ? TEMPLATES.stream().filter(template -> template.select(stack)).findFirst() : Optional.empty(); }
    private static Slot[] slots(CompoundTag data, String key, String override, int count, Class<? extends EnvironmentHost> host) throws ReflectiveOperationException {
        Slot[] slots = new Slot[count]; Arrays.fill(slots, NO_SLOT); var list = data.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(count, list.size()); i++) { CompoundTag slot = list.getCompound(i); String kind = override != null ? override : slot.contains("type") ? slot.getString("type") : li.cil.oc.api.driver.item.Slot.None; int tier = slot.contains("tier") ? slot.getInt("tier") : Tier.Any; Method validator = slot.contains("validate") ? CallbackMethods.resolve(slot.getString("validate"), boolean.class, new Class<?>[]{Container.class, int.class, int.class, ItemStack.class}) : null; slots[i] = new Slot(kind, tier, validator, host); }
        return slots;
    }
    public record Validation(boolean valid, Component progress, List<Component> warnings) { public Validation { warnings = List.copyOf(warnings); } }
    public record Assembly(ItemStack stack, double energy) {}
    public static final class Template {
        public final String name;
        public final Method selector, validator, assembler;
        public final Slot[] containerSlots, upgradeSlots, componentSlots;
        private Template(String name, Method selector, Method validator, Method assembler, Slot[] containers, Slot[] upgrades, Slot[] components) { this.name = name; this.selector = selector; this.validator = validator; this.assembler = assembler; containerSlots = containers; upgradeSlots = upgrades; componentSlots = components; }
        public boolean select(ItemStack stack) { return CallbackMethods.booleanResult(selector, false, stack); }
        public Validation validate(Container inventory) {
            Object raw = CallbackMethods.invoke(validator, null, inventory);
            if (raw instanceof Object[] values && values.length >= 1 && values.length <= 3 && values[0] instanceof Boolean valid) {
                if (values.length >= 2 && !(values[1] instanceof Component)) return new Validation(false, null, List.of());
                if (values.length == 3 && !(values[2] instanceof Component[])) return new Validation(false, null, List.of());
                return new Validation(valid, values.length >= 2 ? (Component) values[1] : null, values.length == 3 ? Arrays.asList((Component[]) values[2]) : List.of());
            }
            return new Validation(false, null, List.of());
        }
        public Assembly assemble(Container inventory) {
            Object raw = CallbackMethods.invoke(assembler, null, inventory);
            if (raw instanceof Object[] values && values.length >= 1 && values.length <= 2 && values[0] instanceof ItemStack stack && (values.length == 1 || values[1] instanceof Number)) return new Assembly(stack, values.length == 2 ? ((Number) values[1]).doubleValue() : 0);
            return new Assembly(ItemStack.EMPTY, 0);
        }
    }
    public static final class Slot {
        public final String kind;
        public final int tier;
        public final Method validator;
        public final Class<? extends EnvironmentHost> hostClass;
        public Slot(String kind, int tier, Method validator, Class<? extends EnvironmentHost> hostClass) { this.kind = kind; this.tier = tier; this.validator = validator; this.hostClass = hostClass; }
        public boolean validate(Container inventory, int index, ItemStack stack) {
            if (validator != null) return CallbackMethods.booleanResult(validator, false, inventory, index, tier, stack);
            var driver = hostClass == null ? Driver.driverFor(stack) : Driver.driverFor(stack, hostClass);
            return driver != null && kind.equals(driver.slot(stack)) && driver.tier(stack) <= tier;
        }
    }
}
