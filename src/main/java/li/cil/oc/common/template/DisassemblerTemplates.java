package li.cil.oc.common.template;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import li.cil.oc.common.integration.CallbackMethods;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/** Original selector and stochastic/guaranteed output contract, kept callable. */
public final class DisassemblerTemplates {
    private static final CopyOnWriteArrayList<Template> TEMPLATES = new CopyOnWriteArrayList<>();
    private DisassemblerTemplates() {}
    public static void add(CompoundTag data) throws ReflectiveOperationException { TEMPLATES.add(new Template(data.getString("name"), CallbackMethods.resolve(data.getString("select"), boolean.class, new Class<?>[]{ItemStack.class}), CallbackMethods.resolve(data.getString("disassemble"), ItemStack.class, ItemStack[].class))); }
    @FunctionalInterface public interface RegistryDisassembler { Disassembly apply(ItemStack stack, ItemStack[] ingredients, HolderLookup.Provider registries); }
    public static void add(String name, Predicate<ItemStack> selector, RegistryDisassembler disassembler) { TEMPLATES.add(new Template(name, selector, disassembler)); }
    public static List<Template> templates() { return List.copyOf(TEMPLATES); }
    public static Optional<Template> select(ItemStack stack) { return TEMPLATES.stream().filter(template -> template.select(stack)).findFirst(); }
    public record Disassembly(Optional<ItemStack[]> ingredients, Optional<ItemStack[]> guaranteedDrops) {}
    public static final class Template {
        public final String name;
        public final Method selector, disassembler;
        private final Predicate<ItemStack> nativeSelector;
        private final RegistryDisassembler nativeDisassembler;
        private Template(String name, Method selector, Method disassembler) { this.name = name; this.selector = selector; this.disassembler = disassembler; nativeSelector = null; nativeDisassembler = null; }
        private Template(String name, Predicate<ItemStack> selector, RegistryDisassembler disassembler) { this.name = name; this.selector = null; this.disassembler = null; nativeSelector = selector; nativeDisassembler = disassembler; }
        public boolean select(ItemStack stack) { return nativeSelector == null ? CallbackMethods.booleanResult(selector, false, stack) : nativeSelector.test(stack); }
        public Disassembly disassemble(ItemStack stack, ItemStack[] ingredients) { return disassemble(stack, ingredients, null); }
        public Disassembly disassemble(ItemStack stack, ItemStack[] ingredients, HolderLookup.Provider registries) {
            if (nativeDisassembler != null) {
                if (registries == null) throw new IllegalArgumentException("Registry access is required to disassemble stored device inventories");
                return nativeDisassembler.apply(stack, ingredients, registries);
            }
            Object result = CallbackMethods.invoke(disassembler, null, stack, ingredients);
            if (result instanceof ItemStack[] stacks) return new Disassembly(Optional.of(stacks), Optional.empty());
            if (result instanceof Object[] values && values.length == 2) { ItemStack[] random = stacks(values[0]), guaranteed = stacks(values[1]); if (random != null && guaranteed != null) return new Disassembly(Optional.of(random), Optional.of(guaranteed)); }
            return new Disassembly(Optional.empty(), Optional.empty());
        }
        private static ItemStack[] stacks(Object value) { return value instanceof ItemStack[] stacks ? stacks : value instanceof ItemStack stack ? new ItemStack[]{stack} : null; }
    }
}
