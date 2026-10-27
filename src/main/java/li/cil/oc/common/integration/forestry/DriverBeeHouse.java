package li.cil.oc.common.integration.forestry;

import forestry.api.IForestryApi;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.core.genetics.ForestrySpeciesTypes;
import forestry.api.core.genetics.IMutation;
import forestry.api.core.genetics.ISpecies;
import forestry.api.core.genetics.ISpeciesType;
import forestry.api.core.genetics.capability.IIndividualHandlerItem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Original bee-housing and mutation callbacks from OC commit 258943363ea8fc4ef14948e6bc6f67375061580c (MIT). */
public final class DriverBeeHouse extends DriverSidedTileEntity {
    @Override public Class<?> getTileEntityClass() { return IBeeHousing.class; }

    @Override public ManagedEnvironment createEnvironment(Level level, BlockPos position, Direction side) {
        return new Environment(level.getBlockEntity(position));
    }

    public static final class Environment extends ForestryEnvironment<BlockEntity> {
        Environment(BlockEntity tile) { super(tile, "bee_housing"); }
        private IBeeHousing housing() { return (IBeeHousing) target(); }
        private ISpeciesType<?, ?> bees() { return IForestryApi.INSTANCE.getGeneticManager().getSpeciesType(ForestrySpeciesTypes.BEE); }

        @Callback(doc = "function():boolean -- Can the bees breed?")
        public Object[] canBreed(Context context, Arguments args) { return new Object[]{housing().getBeekeepingLogic().canWork()}; }

        @Callback(doc = "function():table -- Get the drone")
        public Object[] getDrone(Context context, Arguments args) {
            return new Object[]{IIndividualHandlerItem.getIndividual(housing().getBeeInventory().getDrone())};
        }

        @Callback(doc = "function():table -- Get the queen")
        public Object[] getQueen(Context context, Arguments args) {
            return new Object[]{IIndividualHandlerItem.getIndividual(housing().getBeeInventory().getQueen())};
        }

        @Callback(doc = "function():table -- Get the full breeding list thingy.")
        public Object[] getBeeBreedingData(Context context, Arguments args) {
            ISpeciesType<?, ?> type = bees();
            if (type == null) return null;
            Set<Map<String, Object>> result = new HashSet<>();
            for (IMutation<?> mutation : type.getMutations().getAllMutations()) {
                Map<String, Object> entry = new HashMap<>();
                if (mutation.getFirstParent() != null) entry.put("allele1", mutation.getFirstParent().getDisplayName().getString());
                if (mutation.getSecondParent() != null) entry.put("allele2", mutation.getSecondParent().getDisplayName().getString());
                entry.put("chance", mutation.getChance());
                entry.put("specialConditions", mutation.getSpecialConditions().stream().map(Component::getString).toArray(String[]::new));
                if (mutation.getResult() != null) entry.put("result", mutation.getResult().getDisplayName().getString());
                result.add(entry);
            }
            return new Object[]{result};
        }

        @Callback(doc = "function():table -- Get all known bees mutations")
        public Object[] listAllSpecies(Context context, Arguments args) {
            ISpeciesType<?, ?> type = bees();
            if (type == null) return null;
            Set<ISpecies<?>> result = new HashSet<>();
            // The original method lists mutation results, not every registered species.
            for (IMutation<?> mutation : type.getMutations().getAllMutations()) if (mutation.getResult() != null) result.add(mutation.getResult());
            return new Object[]{result};
        }

        @Callback(doc = "function(beeName:string):table -- Get the parents for a particular mutation")
        public Object[] getBeeParents(Context context, Arguments args) {
            ISpeciesType<?, ?> type = bees();
            if (type == null) return null;
            Set<IMutation<?>> result = new HashSet<>();
            String child = args.checkString(0).toLowerCase(Locale.ROOT);
            for (IMutation<?> mutation : type.getMutations().getAllMutations()) {
                ISpecies<?> species = mutation.getResult();
                if (species != null && (species.id().toString().toLowerCase(Locale.ROOT).equals(child) ||
                    species.getDisplayName().getString().toLowerCase(Locale.ROOT).equals(child))) result.add(mutation);
            }
            return new Object[]{result};
        }
    }
}
