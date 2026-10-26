package li.cil.oc.common.recipe;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Cycle registered loot filesystems using a wrench, preserving the wrench. */
final class LootDiskCyclingRecipe extends CustomRecipe {
    LootDiskCyclingRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static boolean isLootDisk(ItemStack stack) {
        return Recipes.is(stack, "floppy") && !Recipes.customData(stack).getString("oc:lootFactory").isEmpty();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!Recipes.lootRecrafting()) return false;
        int disks = 0, wrenches = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (isLootDisk(stack)) disks++;
            else if (Recipes.isWrench(stack)) wrenches++;
            else return false;
        }
        return disks == 1 && wrenches == 1 && !Recipes.cyclingDisks().isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        if (!Recipes.lootRecrafting()) return ItemStack.EMPTY;
        List<ItemStack> disks = Recipes.cyclingDisks();
        if (disks.isEmpty()) throw new IllegalStateException("Loot disk cycling requires the filesystem factories to be initialized");
        ItemStack source = input.items().stream().filter(LootDiskCyclingRecipe::isLootDisk).findFirst().orElse(ItemStack.EMPTY);
        if (source.isEmpty()) return ItemStack.EMPTY;
        String oldFactory = Recipes.customData(source).getString("oc:lootFactory");
        int oldIndex = -1;
        for (int index = 0; index < disks.size(); index++) {
            if (oldFactory.equals(Recipes.customData(disks.get(index)).getString("oc:lootFactory"))) {
                oldIndex = index;
                break;
            }
        }
        return disks.get((oldIndex + 1) % disks.size()).copyWithCount(1);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            if (Recipes.isWrench(input.getItem(slot))) result.set(slot, input.getItem(slot).copyWithCount(1));
        }
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Recipes.LOOT_CYCLE.get();
    }
}
