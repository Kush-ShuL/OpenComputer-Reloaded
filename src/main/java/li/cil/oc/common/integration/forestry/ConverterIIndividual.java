package li.cil.oc.common.integration.forestry;

import forestry.api.apiculture.ForestryActivityTypes;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.arboriculture.genetics.ITree;
import forestry.api.core.genetics.IGenome;
import forestry.api.core.genetics.IIndividual;
import forestry.api.core.genetics.IIndividualLiving;
import forestry.api.core.genetics.alleles.Allele;
import forestry.api.core.genetics.alleles.BeeChromosomes;
import forestry.api.core.genetics.alleles.ButterflyChromosomes;
import forestry.api.core.genetics.alleles.IChromosome;
import forestry.api.core.genetics.alleles.TreeChromosomes;
import forestry.api.lepidopterology.genetics.IButterfly;
import java.util.HashMap;
import java.util.Map;
import li.cil.oc.api.driver.Converter;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;

/**
 * Java migration of ConverterIIndividual at OpenComputers commit
 * 258943363ea8fc4ef14948e6bc6f67375061580c (MIT; see LICENSE and NOTICE).
 * Original attribution: partially copied from https://github.com/OpenMods/OpenPeripheral.
 * Uses Forestry's public typed chromosomes without packaging Forestry code.
 */
public final class ConverterIIndividual implements Converter {
    @Override public void convert(Object value, Map<Object, Object> output) {
        if (!(value instanceof IIndividual individual)) return;
        String name = individual.getSpecies().getDisplayName().getString();
        output.put("displayName", name);
        output.put("ident", individual.getSpecies().id().toString());
        output.put("isAnalyzed", individual.isAnalyzed());
        output.put("isSecret", individual.isSecret());
        if (individual instanceof IIndividualLiving living) {
            output.put("health", living.getHealth());
            output.put("maxHealth", living.getMaxHealth());
        }
        if (individual instanceof IBee bee) {
            output.put("type", "bee");
            output.put("canSpawn", bee.getMate() != null);
            output.put("generation", bee.getGeneration());
            output.put("hasEffect", bee.hasGlint());
            output.put("isAlive", bee.isAlive());
            output.put("isNatural", bee.isPristine());
        } else if (individual instanceof IButterfly butterfly) {
            output.put("type", "butterfly");
            output.put("size", butterfly.getGenome().getActiveValue(ButterflyChromosomes.SIZE));
        } else if (individual instanceof ITree) {
            output.put("type", "tree");
            output.put("plantType", name);
        } else return;
        if (individual.isAnalyzed()) {
            output.put("active", genes(individual, true));
            output.put("inactive", genes(individual, false));
        }
    }

    private static Map<String, Object> genes(IIndividual individual, boolean active) {
        IGenome genome = individual.getGenome();
        Map<String, Object> result = new HashMap<>();
        if (individual instanceof IBee) {
            put(result, "species", genome, BeeChromosomes.SPECIES, active);
            put(result, "speed", genome, BeeChromosomes.SPEED, active);
            put(result, "lifespan", genome, BeeChromosomes.LIFESPAN, active);
            put(result, "fertility", genome, BeeChromosomes.FERTILITY, active);
            put(result, "temperatureTolerance", genome, BeeChromosomes.TEMPERATURE_TOLERANCE, active);
            Allele<?> activity = active ? genome.getActiveAllele(BeeChromosomes.ACTIVITY) : genome.getInactiveAllele(BeeChromosomes.ACTIVITY);
            result.put("neverSleeps", activity == null ? "missing" : ForestryActivityTypes.CATHEMERAL.equals(activity.value()));
            put(result, "humidityTolerance", genome, BeeChromosomes.HUMIDITY_TOLERANCE, active);
            put(result, "toleratesRain", genome, BeeChromosomes.TOLERATES_RAIN, active);
            put(result, "caveDwelling", genome, BeeChromosomes.CAVE_DWELLING, active);
            put(result, "flowerProvider", genome, BeeChromosomes.FLOWER_TYPE, active);
            put(result, "flowering", genome, BeeChromosomes.POLLINATION, active);
            put(result, "effect", genome, BeeChromosomes.EFFECT, active);
            put(result, "territory", genome, BeeChromosomes.TERRITORY, active);
        } else if (individual instanceof IButterfly) {
            put(result, "species", genome, ButterflyChromosomes.SPECIES, active);
            put(result, "size", genome, ButterflyChromosomes.SIZE, active);
            put(result, "speed", genome, ButterflyChromosomes.SPEED, active);
            put(result, "lifespan", genome, ButterflyChromosomes.LIFESPAN, active);
            put(result, "metabolism", genome, ButterflyChromosomes.METABOLISM, active);
            put(result, "fertility", genome, ButterflyChromosomes.FERTILITY, active);
            put(result, "temperatureTolerance", genome, ButterflyChromosomes.TEMPERATURE_TOLERANCE, active);
            put(result, "neverSleeps", genome, ButterflyChromosomes.NEVER_SLEEPS, active);
            put(result, "humidityTolerance", genome, ButterflyChromosomes.HUMIDITY_TOLERANCE, active);
            put(result, "tolerantFlyer", genome, ButterflyChromosomes.TOLERATES_RAIN, active);
            put(result, "fireResist", genome, ButterflyChromosomes.FIREPROOF, active);
            put(result, "flowerProvider", genome, ButterflyChromosomes.FLOWER_TYPE, active);
            put(result, "effect", genome, ButterflyChromosomes.EFFECT, active);
            put(result, "cocoon", genome, ButterflyChromosomes.COCOON, active);
        } else {
            put(result, "species", genome, TreeChromosomes.SPECIES, active);
            put(result, "fireproof", genome, TreeChromosomes.FIREPROOF, active);
            put(result, "height", genome, TreeChromosomes.HEIGHT, active);
            put(result, "fertility", genome, TreeChromosomes.SAPLINGS, active);
            put(result, "fruits", genome, TreeChromosomes.FRUIT, active);
            put(result, "yield", genome, TreeChromosomes.YIELD, active);
            put(result, "sappiness", genome, TreeChromosomes.SAPPINESS, active);
            put(result, "effect", genome, TreeChromosomes.EFFECT, active);
            put(result, "maturation", genome, TreeChromosomes.MATURATION, active);
            put(result, "girth", genome, TreeChromosomes.GIRTH, active);
        }
        return result;
    }

    private static <V> void put(Map<String, Object> result, String key, IGenome genome, IChromosome<V> chromosome, boolean active) {
        Allele<V> allele = active ? genome.getActiveAllele(chromosome) : genome.getInactiveAllele(chromosome);
        if (allele == null) { result.put(key, "missing"); return; }
        V value = allele.value();
        if (value instanceof Number || value instanceof Boolean) result.put(key, value);
        else if (value instanceof Vec3i area) result.put(key, new int[]{area.getX(), area.getY(), area.getZ()});
        else result.put(key, Component.translatable(chromosome.translationKey(value)).getString());
    }
}
