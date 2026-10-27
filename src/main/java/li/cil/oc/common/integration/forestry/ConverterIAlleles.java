package li.cil.oc.common.integration.forestry;

import forestry.api.core.genetics.IMutation;
import forestry.api.core.genetics.ISpecies;
import java.util.HashMap;
import java.util.Map;
import li.cil.oc.api.driver.Converter;
import net.minecraft.network.chat.Component;

/** Original mutation/species Lua tables from OC commit 258943363ea8fc4ef14948e6bc6f67375061580c (MIT). */
public final class ConverterIAlleles implements Converter {
    @Override public void convert(Object value, Map<Object, Object> output) {
        if (value instanceof IMutation<?> mutation) {
            if (mutation.getFirstParent() != null) output.put("allele1", species(mutation.getFirstParent()));
            if (mutation.getSecondParent() != null) output.put("allele2", species(mutation.getSecondParent()));
            output.put("chance", mutation.getChance());
            output.put("specialConditions", mutation.getSpecialConditions().stream().map(Component::getString).toArray(String[]::new));
        }
        if (value instanceof ISpecies<?> species) output.putAll(species(species));
    }

    private static Map<Object, Object> species(ISpecies<?> species) {
        Map<Object, Object> result = new HashMap<>();
        result.put("name", species.getDisplayName().getString());
        result.put("uid", species.id().toString());
        return result;
    }
}
