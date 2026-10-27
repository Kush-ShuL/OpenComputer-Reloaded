package li.cil.oc.client.integration.jei;

import java.util.List;
import li.cil.oc.api.Items;
import li.cil.oc.common.recipe.ExtendedCraftingRecipe;
import li.cil.oc.common.recipe.LootDiskCyclingRecipe;
import li.cil.oc.common.recipe.Recipes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Real vanilla layout and result, including OC's data-preserving crafting wrappers. */
final class CraftingExtensions {
    private CraftingExtensions() {}

    static final class Extended implements ICraftingCategoryExtension<ExtendedCraftingRecipe> {
        @Override public int getWidth(RecipeHolder<ExtendedCraftingRecipe> holder) {
            return holder.value().baseRecipe() instanceof ShapedRecipe shaped ? shaped.getWidth() : 0;
        }
        @Override public int getHeight(RecipeHolder<ExtendedCraftingRecipe> holder) {
            return holder.value().baseRecipe() instanceof ShapedRecipe shaped ? shaped.getHeight() : 0;
        }
        @Override public void setRecipe(RecipeHolder<ExtendedCraftingRecipe> holder, IRecipeLayoutBuilder builder, ICraftingGridHelper grid, IFocusGroup focuses) {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            grid.createAndSetIngredients(builder, holder.value().getIngredients(), getWidth(holder), getHeight(holder));
            grid.createAndSetOutputs(builder, List.of(holder.value().getResultItem(level.registryAccess())));
            if (getWidth(holder) == 0) builder.setShapeless();
        }
    }

    static final class LootCycle implements ICraftingCategoryExtension<LootDiskCyclingRecipe> {
        @Override public void setRecipe(RecipeHolder<LootDiskCyclingRecipe> holder, IRecipeLayoutBuilder builder, ICraftingGridHelper grid, IFocusGroup focuses) {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            List<ItemStack> disks = Recipes.cyclingDisks(level.registryAccess());
            if (disks.isEmpty()) return;
            ItemStack wrench = Items.get("wrench").createItemStack(1);
            // The original viewer displays every cycling disk as both input and output.
            grid.createAndSetInputs(builder, List.of(disks, List.of(wrench)), 0, 0);
            grid.createAndSetOutputs(builder, disks);
            builder.setShapeless();
        }
    }
}
