package li.cil.oc.common.integration.forestry;

import forestry.api.IForestryApi;
import forestry.api.core.circuits.ICircuit;
import forestry.api.core.circuits.ICircuitBoard;
import forestry.api.core.genetics.IIndividual;
import forestry.api.core.genetics.capability.IIndividualHandlerItem;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import li.cil.oc.api.driver.Converter;
import net.minecraft.world.item.ItemStack;

/** Original individual/circuit item conversion from OC commit 258943363ea8fc4ef14948e6bc6f67375061580c (MIT). */
public final class ConverterItemStack implements Converter {
    @Override public void convert(Object value, Map<Object, Object> output) {
        if (!(value instanceof ItemStack stack)) return;
        IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
        if (individual != null) {
            output.put("individual", individual);
        } else {
            ICircuitBoard board = IForestryApi.INSTANCE.getCircuitManager().getCircuitBoard(stack);
            if (board != null) {
                String[] names = Arrays.stream(board.getCircuits()).filter(Objects::nonNull).map(ICircuit::getId).toArray(String[]::new);
                if (names.length > 0) output.put("circuits", names);
            }
        }
    }
}
