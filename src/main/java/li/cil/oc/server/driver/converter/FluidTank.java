package li.cil.oc.server.driver.converter;

import java.util.Map;
import li.cil.oc.api.driver.Converter;
import net.neoforged.neoforge.fluids.IFluidTank;

/** Modern per-tank replacement for the original FluidTankInfo fields. */
public final class FluidTank implements Converter {
    @Override public void convert(Object value, Map<Object, Object> output) {
        if (!(value instanceof IFluidTank tank)) return;
        output.put("capacity", tank.getCapacity());
        var contents = tank.getFluid();
        if (contents.isEmpty()) output.put("amount", 0);
        else new FluidStack().convert(contents, output);
    }
}
