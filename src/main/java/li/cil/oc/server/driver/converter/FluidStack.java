package li.cil.oc.server.driver.converter;

import java.util.Map;
import li.cil.oc.api.driver.Converter;
import net.minecraft.core.registries.BuiltInRegistries;

/** Original amount/hasTag/name/label inspection without implicit world access. */
public final class FluidStack implements Converter {
    @Override public void convert(Object value, Map<Object, Object> output) {
        if (!(value instanceof net.neoforged.neoforge.fluids.FluidStack stack)) return;
        output.put("amount", stack.getAmount()); output.put("hasTag", !stack.getComponentsPatch().isEmpty());
        if (!stack.isEmpty()) { output.put("name", BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString()); output.put("label", stack.getHoverName().getString()); }
    }
}
