package li.cil.oc.common.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Selects one resolved upstream recipe profile at datapack reload time. */
public record RecipeSetCondition(String set) implements ICondition {
    public static final MapCodec<RecipeSetCondition> CODEC = Codec.STRING.fieldOf("set")
            .xmap(RecipeSetCondition::new, RecipeSetCondition::set);

    @Override
    public boolean test(IContext context) {
        return Recipes.recipeSet().equals(set);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
