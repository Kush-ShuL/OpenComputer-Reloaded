package li.cil.oc.server.driver.converter;

import java.util.LinkedHashMap;
import java.util.Map;
import li.cil.oc.api.driver.Converter;
import li.cil.oc.util.FluidUtils;

/** Original capacity/fluid fields for actual item fluid-handler capabilities. */
public final class FluidContainerItem implements Converter {
    @Override public void convert(Object value, Map<Object, Object> output) {
        if (!(value instanceof net.minecraft.world.item.ItemStack stack)) return;
        var handler = FluidUtils.fluidHandlerOf(stack);
        if (handler == null) return;
        long capacity = 0; int tanks = handler.getTanks();
        for (int i = 0; i < tanks; i++) capacity += handler.getTankCapacity(i);
        output.put("capacity", capacity);
        if (tanks == 1) output.put("fluid", handler.getFluidInTank(0).copy());
        else {
            Object[] fluids = new Object[tanks];
            for (int i = 0; i < tanks; i++) { Map<Object, Object> info = new LinkedHashMap<>(); info.put("capacity", handler.getTankCapacity(i)); new FluidStack().convert(handler.getFluidInTank(i), info); fluids[i] = info; }
            output.put("fluid", fluids);
        }
    }
}
