package li.cil.oc.common.integration.mekanism;

import java.lang.reflect.Method;
import java.util.Map;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.Converter;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Java adaptation of OpenComputers' original ConverterGasStack for Mekanism's
 * unified chemical API. Copyright (c) 2013-2015 Florian "Sangar" Nücke; MIT,
 * see LICENSE. Only public API classes, methods and fields are accessed.
 */
public final class ChemicalStackConverter implements Converter {
    private final Class<?> stackType;
    private final Method getChemical;
    private final Method getAmount;
    private final Method getTextComponent;
    private final Registry<Object> chemicals;

    private ChemicalStackConverter(Class<?> stackType, Method getChemical, Method getAmount,
                                   Method getTextComponent, Registry<Object> chemicals) {
        this.stackType = stackType;
        this.getChemical = getChemical;
        this.getAmount = getAmount;
        this.getTextComponent = getTextComponent;
        this.chemicals = chemicals;
    }

    /** Resolve the public API only after the loader and blacklist checks. */
    @SuppressWarnings("unchecked")
    static ChemicalStackConverter create() throws ReflectiveOperationException {
        ClassLoader loader = ChemicalStackConverter.class.getClassLoader();
        Class<?> stackType = Class.forName("mekanism.api.chemical.ChemicalStack", false, loader);
        Method chemical = stackType.getMethod("getChemical");
        Method amount = stackType.getMethod("getAmount");
        Method text = stackType.getMethod("getTextComponent");
        if (amount.getReturnType() != long.class || !Component.class.isAssignableFrom(text.getReturnType())) {
            throw new NoSuchMethodException("ChemicalStack must expose getAmount(): long and getTextComponent(): Component");
        }
        Object registry = Class.forName("mekanism.api.MekanismAPI", true, loader)
            .getField("CHEMICAL_REGISTRY").get(null);
        if (!(registry instanceof Registry<?>)) {
            throw new NoSuchFieldException("MekanismAPI.CHEMICAL_REGISTRY must be a Minecraft Registry");
        }
        return new ChemicalStackConverter(stackType, chemical, amount, text, (Registry<Object>) registry);
    }

    @Override
    public void convert(Object value, Map<Object, Object> output) {
        if (!stackType.isInstance(value)) return;
        try {
            Object chemical = getChemical.invoke(value);
            // Keep Mekanism's full long value at the conversion boundary.
            long amount = (long) getAmount.invoke(value);
            ResourceLocation name = chemical == null ? null : chemicals.getKey(chemical);
            String label = chemical == null ? null : ((Component) getTextComponent.invoke(value)).getString();

            if (Settings.get().insertIdsInConverters) output.put("id", chemicals.getId(chemical));
            output.put("amount", amount);
            if (chemical != null) {
                if (name != null) output.put("name", name.toString());
                output.put("label", label);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read Mekanism's public ChemicalStack API", exception);
        }
    }
}
