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
    private static final java.util.Map<net.minecraft.core.HolderLookup.Provider, List<ItemStack>> WORLD_LOOT_DISKS = new java.util.WeakHashMap<>();
    private static volatile List<ItemStack> clientWorldLootDisks = List.of();
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

    /** Server templates are scoped to its explicit registry provider, separate from the local client. */
    public static void setWorldLootDisks(net.minecraft.core.HolderLookup.Provider registries, List<ItemStack> disks) {
        synchronized (WORLD_LOOT_DISKS) {
            if (disks.isEmpty()) WORLD_LOOT_DISKS.remove(registries);
            else WORLD_LOOT_DISKS.put(registries, disks.stream().map(disk -> disk.copyWithCount(1)).toList());
        }
    }

    public static void setClientWorldLootDisks(List<ItemStack> disks) {
        clientWorldLootDisks = disks.stream().map(disk -> disk.copyWithCount(1)).toList();
    }

    public static List<ItemStack> clientWorldLootDisks() {
        return clientWorldLootDisks.stream().map(ItemStack::copy).toList();
    }

    static List<ItemStack> cyclingDisks() {
        return cyclingDisks(null);
    }

    public static List<ItemStack> cyclingDisks(net.minecraft.core.HolderLookup.Provider registries) {
        List<ItemStack> worldDisks;
        synchronized (WORLD_LOOT_DISKS) { worldDisks = WORLD_LOOT_DISKS.get(registries); }
        if (worldDisks == null) worldDisks = li.cil.oc.util.SideTracker.isClient() ? clientWorldLootDisks : List.of();
        java.util.LinkedHashMap<String, ItemStack> result = new java.util.LinkedHashMap<>();
        for (ItemStack disk : CYCLING_DISKS) result.put(customData(disk).getString("oc:lootFactory"), disk.copy());
        for (ItemStack disk : worldDisks) result.put(customData(disk).getString("oc:lootFactory"), disk.copy());
        return new ArrayList<>(result.values());
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
        return li.cil.oc.common.integration.WrenchTools.isWrench(stack) || is(stack, "wrench");
    }
}
