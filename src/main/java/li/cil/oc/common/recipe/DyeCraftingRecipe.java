package li.cil.oc.common.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Original brightness-preserving dye mixing for cables and hover boots. */
final class DyeCraftingRecipe extends CustomRecipe {
    private final ResourceLocation target;
    private final boolean remove;

    DyeCraftingRecipe(CraftingBookCategory category, ResourceLocation target, boolean remove) {
        super(category);
        this.target = target;
        this.remove = remove;
    }

    private boolean isTarget(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(target);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int targets = 0;
        int dyes = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (isTarget(stack)) targets++;
            else if (remove ? stack.is(Items.WATER_BUCKET) : DyeColor.getColor(stack) != null) dyes++;
            else return false;
        }
        return targets == 1 && (remove ? dyes == 1 : dyes > 0);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack targetStack = ItemStack.EMPTY;
        List<DyeColor> dyes = new ArrayList<>();
        int other = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (isTarget(stack)) {
                if (!targetStack.isEmpty()) return ItemStack.EMPTY;
                targetStack = stack.copyWithCount(1);
            } else if (remove) {
                if (!stack.is(Items.WATER_BUCKET)) return ItemStack.EMPTY;
                other++;
            } else {
                DyeColor dye = DyeColor.getColor(stack);
                if (dye == null) return ItemStack.EMPTY;
                dyes.add(dye);
            }
        }
        if (targetStack.isEmpty() || (remove ? other != 1 : dyes.isEmpty())) return ItemStack.EMPTY;
        CompoundTag root = Recipes.customData(targetStack);
        CompoundTag display = root.getCompound("display");
        if (remove) {
            targetStack.remove(DataComponents.DYED_COLOR);
            display.remove("color");
        } else {
            int red = 0, green = 0, blue = 0, maximum = 0, count = 0;
            DyedItemColor current = targetStack.get(DataComponents.DYED_COLOR);
            Integer old = current != null ? current.rgb() : display.contains("color", Tag.TAG_ANY_NUMERIC) ? display.getInt("color") : null;
            if (old != null) {
                int r = old >> 16 & 255, g = old >> 8 & 255, b = old & 255;
                red += r; green += g; blue += b; maximum += Math.max(r, Math.max(g, b)); count++;
            }
            for (DyeColor dye : dyes) {
                int rgb = dye.getTextureDiffuseColor();
                int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
                red += r; green += g; blue += b; maximum += Math.max(r, Math.max(g, b)); count++;
            }
            red /= count; green /= count; blue /= count;
            float brightness = (float) maximum / count;
            float divisor = Math.max(red, Math.max(green, blue));
            int rgb = 0;
            if (divisor != 0) {
                red = (int) (red * brightness / divisor);
                green = (int) (green * brightness / divisor);
                blue = (int) (blue * brightness / divisor);
                rgb = red << 16 | green << 8 | blue;
            }
            display.putInt("color", rgb);
            targetStack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, false));
        }
        if (display.isEmpty()) root.remove("display");
        else root.put("display", display);
        Recipes.customData(targetStack, root);
        return targetStack;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return (remove ? Recipes.DECOLORIZE : Recipes.COLORIZE).get();
    }

    static final class Serializer implements RecipeSerializer<DyeCraftingRecipe> {
        private final MapCodec<DyeCraftingRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, DyeCraftingRecipe> stream;

        Serializer(boolean remove) {
            codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    CraftingBookCategory.CODEC.optionalFieldOf("category", CraftingBookCategory.MISC).forGetter(DyeCraftingRecipe::category),
                    ResourceLocation.CODEC.fieldOf("target").forGetter(recipe -> recipe.target)
            ).apply(instance, (category, target) -> new DyeCraftingRecipe(category, target, remove)));
            stream = StreamCodec.of((buffer, recipe) -> {
                buffer.writeEnum(recipe.category());
                buffer.writeResourceLocation(recipe.target);
            }, buffer -> new DyeCraftingRecipe(buffer.readEnum(CraftingBookCategory.class), buffer.readResourceLocation(), remove));
        }

        @Override public MapCodec<DyeCraftingRecipe> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, DyeCraftingRecipe> streamCodec() { return stream; }
    }
}
