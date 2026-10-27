package li.cil.oc.client.integration.jei;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import li.cil.oc.Settings;
import li.cil.oc.api.Items;
import li.cil.oc.client.gui.RelayScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Optional JEI integration; discovered by JEI only, never linked by common startup. */
@JeiPlugin
public final class OpenComputersJeiPlugin implements IModPlugin {
    private static final Set<String> HIDDEN = Set.of("present", "robot", "drone", "tablet", "microcontroller", "print", "floppycontainer");
    private IJeiRuntime runtime;
    private Map<String, ItemStack> worldDisks = Map.of();
    private final Map<String, ItemStack> bundledDisks = new LinkedHashMap<>();

    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("opencomputers", "jei"); }

    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        if (!JeiIntegration.enabled()) return;
        for (String name : List.of("microcontroller", "robot", "drone", "tablet")) {
            var info = Items.get(name);
            if (info != null) registration.registerSubtypeInterpreter(info.createItemStack(1).getItem(), new ISubtypeInterpreter<ItemStack>() {
                @Override public Object getSubtypeData(ItemStack stack, UidContext context) {
                    return stack.getComponentsPatch().isEmpty() ? null : stack.getComponentsPatch();
                }
                @Override public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) { return data(stack).toString(); }
            });
        }
        var floppy = Items.get("floppy");
        if (floppy != null) registration.registerSubtypeInterpreter(floppy.item(), new ISubtypeInterpreter<ItemStack>() {
            @Override public Object getSubtypeData(ItemStack stack, UidContext context) {
                String factory = factory(stack);
                return factory.isEmpty() ? null : factory;
            }
            @Override public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                String factory = factory(stack);
                if (factory.isEmpty()) return "";
                CompoundTag data = new CompoundTag(); data.putString("oc:lootFactory", factory); return data.toString();
            }
        });
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!JeiIntegration.enabled()) return;
        var gui = registration.getJeiHelpers().getGuiHelper();
        // Registration order preserves the original Manual-before-API usage navigation.
        registration.addRecipeCategories(new DocumentationCategories.ManualCategory(gui), new DocumentationCategories.CallbackCategory(gui));
    }

    @Override public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        if (!JeiIntegration.enabled()) return;
        registration.getCraftingCategory().addExtension(li.cil.oc.common.recipe.ExtendedCraftingRecipe.class, new CraftingExtensions.Extended());
        if (Settings.get().lootRecrafting) registration.getCraftingCategory().addExtension(li.cil.oc.common.recipe.LootDiskCyclingRecipe.class, new CraftingExtensions.LootCycle());
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        if (!JeiIntegration.enabled()) return;
        var manager = registration.getIngredientManager();
        var ingredients = manager.getAllItemStacks().stream().filter(stack -> !hiddenBase(stack)).toList();
        registration.addRecipes(DocumentationCategories.MANUAL, DocumentationCategories.manuals(ingredients));
        registration.addRecipes(DocumentationCategories.CALLBACKS, DocumentationCategories.callbacks(ingredients));
    }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        if (!JeiIntegration.enabled()) return;
        registration.addGuiContainerHandler(RelayScreen.class, new IGuiContainerHandler<>() {
            @Override public List<Rect2i> getGuiExtraAreas(RelayScreen screen) {
                return List.of(new Rect2i(screen.getGuiLeft() + 176, screen.getGuiTop() + 10, 23, 26));
            }
        });
    }

    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (!JeiIntegration.enabled()) return;
        this.runtime = runtime;
        worldDisks = Map.of(); bundledDisks.clear();
        for (var disk : li.cil.oc.common.init.Items.INSTANCE.lootDisks()) bundledDisks.put(factory(disk.stack()), disk.stack());
        List<ItemStack> hidden = runtime.getIngredientManager().getAllItemStacks().stream().filter(OpenComputersJeiPlugin::hiddenBase).toList();
        if (!hidden.isEmpty()) runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        JeiGui.runtime(runtime);
        JeiIntegration.attach(this::refreshWorldDisks);
        refreshWorldDisks();
    }

    @Override public void onRuntimeUnavailable() {
        JeiIntegration.attach(null);
        JeiGui.runtime(null);
        runtime = null; worldDisks = Map.of(); bundledDisks.clear();
    }

    private void refreshWorldDisks() {
        if (runtime == null) return;
        Map<String, ItemStack> next = new LinkedHashMap<>();
        for (ItemStack stack : li.cil.oc.common.integration.LootIntegration.clientDisks()) if (!factory(stack).isEmpty()) next.put(factory(stack), stack);
        if (sameDisks(worldDisks, next)) return;
        var ingredients = runtime.getIngredientManager();
        if (!worldDisks.isEmpty()) {
            ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, worldDisks.values());
            List<ItemStack> restore = worldDisks.keySet().stream().filter(bundledDisks::containsKey).map(bundledDisks::get).toList();
            if (!restore.isEmpty()) ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, restore);
        }
        if (!next.isEmpty()) ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, next.values());
        worldDisks = next;
    }

    private static boolean sameDisks(Map<String, ItemStack> left, Map<String, ItemStack> right) {
        return left.keySet().equals(right.keySet()) && left.entrySet().stream().allMatch(entry -> ItemStack.isSameItemSameComponents(entry.getValue(), right.get(entry.getKey())));
    }

    private static boolean hiddenBase(ItemStack stack) {
        var info = Items.get(stack);
        return info != null && (li.cil.oc.common.recipe.RecipeConfigLoader.hidden(info.name())
                || HIDDEN.contains(info.name()) && ItemStack.isSameItemSameComponents(stack, info.createItemStack(1)));
    }

    private static CompoundTag data(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    private static String factory(ItemStack stack) { return data(stack).getString("oc:lootFactory"); }
}
