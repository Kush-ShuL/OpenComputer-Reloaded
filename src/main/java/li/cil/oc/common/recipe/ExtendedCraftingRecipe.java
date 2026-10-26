package li.cil.oc.common.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Vanilla ingredient matching plus OpenComputers' data-preserving assembly. */
public final class ExtendedCraftingRecipe implements CraftingRecipe {
    private final CraftingRecipe base;
    private final RecipeOperation operation;
    private final boolean shaped;

    ExtendedCraftingRecipe(CraftingRecipe base, RecipeOperation operation, boolean shaped) {
        this.base = base;
        this.operation = operation;
        this.shaped = shaped;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return base.matches(input, level) && operation.canApply(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        if (!operation.canApply(input)) return ItemStack.EMPTY;
        return operation.assemble(base.assemble(input, registries), input, registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return base.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return operation.displayResult(base.getResultItem(registries));
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return base.getIngredients();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return base.getRemainingItems(input);
    }

    @Override
    public CraftingBookCategory category() {
        return base.category();
    }

    @Override
    public String getGroup() {
        return base.getGroup();
    }

    @Override
    public boolean showNotification() {
        return base.showNotification();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return (shaped ? Recipes.SHAPED : Recipes.SHAPELESS).get();
    }

    public static final class Serializer<T extends CraftingRecipe> implements RecipeSerializer<ExtendedCraftingRecipe> {
        private final MapCodec<ExtendedCraftingRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, ExtendedCraftingRecipe> stream;

        public Serializer(RecipeSerializer<T> vanilla, boolean shaped) {
            codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    vanilla.codec().forGetter((ExtendedCraftingRecipe recipe) -> cast(recipe.base)),
                    RecipeOperation.CODEC.forGetter(recipe -> recipe.operation)
            ).apply(instance, (base, operation) -> new ExtendedCraftingRecipe(base, operation, shaped)));
            stream = StreamCodec.of((buffer, recipe) -> {
                vanilla.streamCodec().encode(buffer, cast(recipe.base));
                recipe.operation.write(buffer);
            }, buffer -> new ExtendedCraftingRecipe(vanilla.streamCodec().decode(buffer), RecipeOperation.read(buffer), shaped));
        }

        @SuppressWarnings("unchecked")
        private T cast(CraftingRecipe recipe) {
            return (T) recipe;
        }

        @Override
        public MapCodec<ExtendedCraftingRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ExtendedCraftingRecipe> streamCodec() {
            return stream;
        }
    }
}
