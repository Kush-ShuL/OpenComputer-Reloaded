package li.cil.oc.common.recipe;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import li.cil.oc.api.Items;
import li.cil.oc.api.detail.ItemInfo;
import li.cil.oc.api.machine.Architecture;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Original OpenComputers crafting behavior and its NeoForge registrations. */
public final class Recipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, "opencomputers");
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, "opencomputers");

    public static final Supplier<RecipeSerializer<ExtendedCraftingRecipe>> SHAPED = SERIALIZERS.register(
            "crafting_shaped", () -> new ExtendedCraftingRecipe.Serializer<>(new net.minecraft.world.item.crafting.ShapedRecipe.Serializer(), true));
    public static final Supplier<RecipeSerializer<ExtendedCraftingRecipe>> SHAPELESS = SERIALIZERS.register(
            "crafting_shapeless", () -> new ExtendedCraftingRecipe.Serializer<>(new net.minecraft.world.item.crafting.ShapelessRecipe.Serializer(), false));
    public static final Supplier<RecipeSerializer<DyeCraftingRecipe>> COLORIZE = SERIALIZERS.register(
            "colorize", () -> new DyeCraftingRecipe.Serializer(false));
    public static final Supplier<RecipeSerializer<DyeCraftingRecipe>> DECOLORIZE = SERIALIZERS.register(
            "decolorize", () -> new DyeCraftingRecipe.Serializer(true));
    public static final Supplier<RecipeSerializer<LootDiskCyclingRecipe>> LOOT_CYCLE = SERIALIZERS.register(
            "loot_cycle", () -> new SimpleCraftingRecipeSerializer<>(LootDiskCyclingRecipe::new));

    private static final CopyOnWriteArrayList<ItemStack> CYCLING_DISKS = new CopyOnWriteArrayList<>();
    private static volatile Supplier<String> recipeSet = () -> "default";
    private static volatile BooleanSupplier lootRecrafting = () -> true;
    private static volatile Supplier<Class<? extends Architecture>> defaultArchitecture = () -> li.cil.oc.api.Machine.LuaArchitecture;
    private static final TagKey<Item> WRENCHES = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/wrench"));

    static {
        CONDITIONS.register("recipe_set", () -> RecipeSetCondition.CODEC);
    }

    private Recipes() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
        CONDITIONS.register(modBus);
    }

    /** Bind settings before recipes are loaded. Values remain live across reloads. */
    public static void setRecipeSetSupplier(Supplier<String> supplier) {
        recipeSet = Objects.requireNonNull(supplier);
    }

    public static void setLootRecraftingSupplier(BooleanSupplier supplier) {
        lootRecrafting = Objects.requireNonNull(supplier);
    }

    public static void setDefaultArchitectureSupplier(Supplier<Class<? extends Architecture>> supplier) {
        defaultArchitecture = Objects.requireNonNull(supplier);
    }

    static Class<? extends Architecture> defaultArchitecture() {
        Class<? extends Architecture> architecture = defaultArchitecture.get();
        if (architecture == null) {
            throw new IllegalStateException("OpenComputers architectures must be initialized before crafting a processor");
        }
        return architecture;
    }

    static String recipeSet() {
        String value = Objects.requireNonNull(recipeSet.get(), "OpenComputers recipe set").toLowerCase(Locale.ROOT);
        if (!List.of("default", "hardmode", "peaceful", "gregtech").contains(value)) {
            throw new IllegalArgumentException("Unknown OpenComputers recipe set: " + value);
        }
        return value;
    }

    static boolean lootRecrafting() {
        return lootRecrafting.getAsBoolean();
    }

    /** Call for every disk registered by the Items API; data and order are retained. */
    public static void registerLootDisk(ItemStack disk, boolean doRecipeCycling) {
        if (disk.isEmpty()) throw new IllegalArgumentException("A registered loot disk cannot be empty");
        String factory = customData(disk).getString("oc:lootFactory");
        if (factory.isEmpty()) throw new IllegalArgumentException("A loot disk must name its oc:lootFactory");
        if (doRecipeCycling) {
            CYCLING_DISKS.removeIf(old -> factory.equals(customData(old).getString("oc:lootFactory")));
            CYCLING_DISKS.add(disk.copyWithCount(1));
        }
    }

    /** Clear registrations when rebuilding the world-specific loot disk list. */
    public static void clearLootDisks() {
        CYCLING_DISKS.clear();
    }

    static List<ItemStack> cyclingDisks() {
        List<ItemStack> result = new ArrayList<>(CYCLING_DISKS.size());
        for (ItemStack disk : CYCLING_DISKS) result.add(disk.copy());
        return result;
    }

    static ItemStack lootDisk(String factory) {
        requireItems();
        for (ItemStack disk : CYCLING_DISKS) {
            if (factory.equals(customData(disk).getString("oc:lootFactory"))) return disk.copy();
        }
        String name = factory.startsWith("opencomputers:") ? factory.substring("opencomputers:".length()) : factory;
        ItemInfo info = Items.get(name);
        if (info != null) {
            ItemStack disk = info.createItemStack(1);
            if (!disk.isEmpty() && factory.equals(customData(disk).getString("oc:lootFactory"))) return disk;
        }
        throw new IllegalStateException("Loot filesystem factory has not been registered: " + factory);
    }

    static ItemStack definedItem(String name) {
        requireItems();
        ItemInfo info = Items.get(name);
        if (info == null) throw new IllegalStateException("OpenComputers item definition has not been registered: " + name);
        ItemStack stack = info.createItemStack(1);
        if (stack.isEmpty()) throw new IllegalStateException("OpenComputers item definition created an empty stack: " + name);
        return stack;
    }

    private static void requireItems() {
        if (li.cil.oc.api.API.items == null) throw new IllegalStateException("OpenComputers Items API is not initialized");
    }

    static CompoundTag customData(ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
    }

    static void customData(ItemStack stack, CompoundTag data) {
        if (data.isEmpty()) stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        else stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
    }

    static boolean is(ItemStack stack, String name) {
        return !stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                .equals(ResourceLocation.fromNamespaceAndPath("opencomputers", name));
    }

    static boolean isWrench(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof li.cil.oc.api.internal.Wrench
                || is(stack, "wrench") || stack.is(WRENCHES));
    }
}
