package li.cil.oc.common.recipe;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigIncluderFile;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigSyntax;
import com.typesafe.config.ConfigValueType;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import li.cil.oc.OpenComputers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Runtime migration of the original HOCON recipe registry. */
public final class RecipeConfigLoader {
    private static final String RESOURCE_ROOT = "/assets/opencomputers/recipes/";
    private static final List<String> PROFILES = List.of("default", "hardmode", "peaceful", "gregtech");
    private static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static final Map<String, BiFunction<ItemStack, Config, Recipe<?>>> HANDLERS = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static final Map<String, String> INGREDIENTS = new LinkedHashMap<>();
    private static volatile Set<String> hidden = Set.of();
    private static Path directory;

    static {
        registerRecipeHandler("shaped", (output, config) -> crafting(output, config, true));
        registerRecipeHandler("shapeless", (output, config) -> crafting(output, config, false));
        registerRecipeHandler("furnace", (output, config) -> new SmeltingRecipe("", CookingBookCategory.MISC,
                parseIngredient(config.getAnyRef("input")), output, 0, 200));
    }

    private RecipeConfigLoader() {}

    /** Internal integration extension, equivalent to the original recipe handlers. */
    public static void registerRecipeHandler(String name, BiFunction<ItemStack, Config, Recipe<?>> handler) {
        HANDLERS.put(name, handler);
    }

    public static void initialize(Path configDirectory) {
        directory = configDirectory;
        try {
            Files.createDirectories(directory);
            for (String profile : PROFILES) copyResource(profile + ".recipes", directory.resolve(profile + ".recipes"));
            Path user = directory.resolve("user.recipes");
            if (!Files.exists(user)) {
                try (var input = RecipeConfigLoader.class.getResourceAsStream(RESOURCE_ROOT + "user.recipes")) {
                    String template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    if (!Recipes.recipeSet().equals("default")) template = template.replace("include file(\"default.recipes\")", "include file(\"" + Recipes.recipeSet() + ".recipes\")");
                    Files.writeString(user, template, StandardCharsets.UTF_8);
                }
            }
            try (var reader = new InputStreamReader(RecipeConfigLoader.class.getResourceAsStream("/assets/opencomputers/resource-mapping.json"), StandardCharsets.UTF_8)) {
                JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("legacy_ore_aliases")
                        .entrySet().forEach(entry -> ALIASES.put(entry.getKey(), "opencomputers:" + entry.getValue().getAsString()));
            }
            try (var reader = new InputStreamReader(RecipeConfigLoader.class.getResourceAsStream(RESOURCE_ROOT + "ingredient-mapping.json"), StandardCharsets.UTF_8)) {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                        .forEach(entry -> INGREDIENTS.put(entry.getKey(), entry.getValue().getAsString()));
            }
            updateHidden(load());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not initialize OpenComputers recipe files", exception);
        }
        NeoForge.EVENT_BUS.addListener(RecipeConfigLoader::addReloadListener);
    }

    private static void copyResource(String name, Path target) throws IOException {
        try (var input = RecipeConfigLoader.class.getResourceAsStream(RESOURCE_ROOT + name)) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Config load() {
        var includes = new FileIncludes();
        return ConfigFactory.parseFile(directory.resolve("user.recipes").toFile(),
                includes.options()).resolve();
    }

    /** .recipes has no standard Config extension; every include must use CONF. */
    private static final class FileIncludes implements ConfigIncluder, ConfigIncluderFile {
        private ConfigIncluder fallback;
        ConfigParseOptions options() {
            return ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF).setAllowMissing(false).setIncluder(this);
        }
        @Override public ConfigIncluder withFallback(ConfigIncluder fallback) { this.fallback = fallback; return this; }
        @Override public ConfigObject include(ConfigIncludeContext context, String name) { return fallback.include(context, name); }
        @Override public ConfigObject includeFile(ConfigIncludeContext context, java.io.File file) {
            Path path = file.isAbsolute() ? file.toPath() : directory.resolve(file.getPath());
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return ConfigFactory.parseReader(reader, options().setOriginDescription(path.toString())).root();
            } catch (IOException exception) { throw new UncheckedIOException("Could not read recipe include " + path, exception); }
        }
    }

    private static void updateHidden(Config config) {
        Set<String> next = new LinkedHashSet<>();
        config.root().forEach((name, value) -> {
            if (value.valueType() == ConfigValueType.BOOLEAN && !((Boolean) value.unwrapped())) next.add(name.toLowerCase(Locale.ROOT));
        });
        hidden = Set.copyOf(next);
    }

    public static boolean hidden(String name) { return hidden.contains(name); }

    private static void addReloadListener(AddReloadListenerEvent event) {
        RecipeManager manager = event.getServerResources().getRecipeManager();
        event.addListener(new SimplePreparableReloadListener<Config>() {
            @Override protected Config prepare(ResourceManager resources, ProfilerFiller profiler) { return load(); }
            @Override protected void apply(Config config, ResourceManager resources, ProfilerFiller profiler) {
                applyRecipes(manager, resources, config);
            }
        });
    }

    private static void applyRecipes(RecipeManager manager, ResourceManager resources, Config config) {
        updateHidden(config);
        Map<ResourceLocation, RecipeHolder<?>> recipes = new LinkedHashMap<>();
        for (var holder : manager.getRecipes()) {
            String path = holder.id().getPath();
            boolean bundled = holder.id().getNamespace().equals(OpenComputers.ID)
                    && PROFILES.stream().anyMatch(profile -> path.startsWith(profile + "/"));
            if (!bundled || dataPackOverride(resources, holder.id())) recipes.put(holder.id(), holder);
        }
        config.root().forEach((name, value) -> {
            if (value.valueType() != ConfigValueType.OBJECT) return;
            ItemStack result = output(name);
            if (!result.isEmpty()) add(recipes, resources, name.toLowerCase(Locale.ROOT), result, ((com.typesafe.config.ConfigObject) value).toConfig(), "");
        });
        if (config.hasPath("lootdisks")) for (Config recipe : config.getConfigList("lootdisks")) {
            String factory = recipe.getString("name");
            ItemStack disk = li.cil.oc.common.init.Items.INSTANCE.lootDisks().stream().map(li.cil.oc.common.init.Items.LootDisk::stack)
                    .filter(stack -> Recipes.customData(stack).getString("oc:lootFactory").equals(factory)).findFirst().orElse(ItemStack.EMPTY);
            if (!disk.isEmpty()) add(recipes, resources, "loot/" + ResourceLocation.parse(factory).getPath(), disk, recipe, factory);
        }
        if (config.hasPath("generic")) {
            int index = 0;
            for (Config recipe : config.getConfigList("generic")) {
                String name = "generic/" + index++;
                try { add(recipes, resources, name, parseItem(recipe.getAnyRef("result")), recipe, ""); }
                catch (IllegalArgumentException exception) { OpenComputers.LOGGER.error("Invalid OpenComputers recipe {}: {}", name, exception.getMessage()); }
            }
        }
        manager.replaceRecipes(recipes.values());
        OpenComputers.LOGGER.info("Loaded OpenComputers recipes from {}", directory.resolve("user.recipes"));
    }

    private static boolean dataPackOverride(ResourceManager resources, ResourceLocation id) {
        return resources.getResource(id.withPath(path -> "recipe/" + path + ".json"))
                .map(resource -> !resource.sourcePackId().equals("mod/" + OpenComputers.ID)).orElse(false);
    }

    private static ItemStack output(String name) {
        if (name.equals("ingotiron")) return new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        var info = li.cil.oc.api.Items.get(name.equals("luabios") ? "eeprom" : name.toLowerCase(Locale.ROOT));
        return info == null ? ItemStack.EMPTY : info.createItemStack(1);
    }

    private static void add(Map<ResourceLocation, RecipeHolder<?>> recipes, ResourceManager resources, String name, ItemStack output, Config config, String disk) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, Recipes.recipeSet() + "/" + name);
        if (dataPackOverride(resources, id)) return;
        try {
            String type = config.hasPath("type") ? config.getString("type") : "shaped";
            var handler = HANDLERS.get(type);
            if (handler == null) throw new IllegalArgumentException("No registered recipe handler: " + type);
            Recipe<?> recipe = handler.apply(output.copyWithCount(config.hasPath("output") ? config.getInt("output") : 1), config);
            if (recipe instanceof CraftingRecipe crafting) {
                var info = li.cil.oc.api.Items.get(output);
                String resultName = name.equals("luabios") ? name : info == null ? name : info.name();
                String operation = !disk.isEmpty() ? "loot_disk" : switch (resultName) {
                    case "cpu1", "cpu2", "cpu3", "apu1", "apu2", "apucreative" -> "default_architecture";
                    case "navigationupgrade" -> "navigation_map";
                    case "linkedcard" -> "linked_cards";
                    case "luabios" -> "lua_bios";
                    default -> "";
                };
                if (!operation.isEmpty()) recipe = new ExtendedCraftingRecipe(crafting,
                        new RecipeOperation(operation, 1, Either.right(""), disk), crafting instanceof ShapedRecipe);
            }
            recipes.put(id, new RecipeHolder<>(id, recipe));
        } catch (IllegalArgumentException | com.typesafe.config.ConfigException exception) {
            OpenComputers.LOGGER.error("Invalid OpenComputers recipe {}: {}", name, exception.getMessage());
        }
    }

    private static CraftingRecipe crafting(ItemStack output, Config config, boolean shaped) {
        Object input = config.getAnyRef("input");
        if (!shaped) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (Object entry : input instanceof List<?> list ? list : List.of(input)) ingredients.add(parseIngredient(entry));
            return new ShapelessRecipe("", CraftingBookCategory.MISC, output, ingredients);
        }
        Map<Character, Ingredient> keys = new LinkedHashMap<>();
        List<String> rows = new ArrayList<>();
        for (Object row : (List<?>) input) {
            StringBuilder pattern = new StringBuilder();
            for (Object entry : (List<?>) row) {
                Ingredient ingredient = parseIngredient(entry);
                if (ingredient == Ingredient.EMPTY) pattern.append(' ');
                else {
                    char key = (char) ('A' + keys.size());
                    keys.put(key, ingredient);
                    pattern.append(key);
                }
            }
            rows.add(pattern.toString());
        }
        return new ShapedRecipe("", CraftingBookCategory.MISC, ShapedRecipePattern.of(keys, rows), output);
    }

    public static Ingredient parseIngredient(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("oreDict")) return parseIngredient(map.get("oreDict"));
            if (map.containsKey("tag")) return tag(map.get("tag").toString());
            return Ingredient.of(parseItem(value));
        }
        String name = value.toString();
        if (name.isBlank()) return Ingredient.EMPTY;
        if (name.startsWith("#")) return tag(name.substring(1));
        if (ALIASES.containsKey(name)) return tag(ALIASES.get(name));
        String mapped = INGREDIENTS.get(name);
        if (mapped != null) return mapped.startsWith("#") ? tag(mapped.substring(1)) : Ingredient.of(parseItem(mapped));
        return Ingredient.of(parseItem(name));
    }

    private static Ingredient tag(String id) { return Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse(id))); }

    private static ItemStack parseItem(Object value) {
        Object identifier = value;
        Object metadata = 0;
        if (value instanceof Map<?, ?> map) {
            identifier = map.containsKey("item") ? map.get("item") : map.get("block");
            if (map.containsKey("subID")) metadata = map.get("subID");
        }
        if (!(identifier instanceof String name)) throw new IllegalArgumentException("Use a modern item resource ID: " + identifier);
        int subId = metadata.equals("any") ? 0 : Integer.parseInt(metadata.toString());
        if (subId != 0 || name.equals("wool") || name.equals("minecraft:wool") || name.equals("dyePowder") || name.equals("minecraft:dye")) {
            String vanilla = name.startsWith("minecraft:") ? name.substring(10) : name;
            name = switch (vanilla) {
                case "coal" -> subId == 1 ? "minecraft:charcoal" : "minecraft:coal";
                case "wool" -> "minecraft:" + COLORS.get(subId) + "_wool";
                case "dyePowder", "dye" -> "minecraft:" + switch (subId) {
                    case 0 -> "ink_sac";
                    case 3 -> "cocoa_beans";
                    case 4 -> "lapis_lazuli";
                    case 15 -> "bone_meal";
                    default -> COLORS.get(15 - subId) + "_dye";
                };
                default -> throw new IllegalArgumentException("Replace legacy metadata with a modern item ID: " + value);
            };
        }
        ResourceLocation id = ResourceLocation.parse(name);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown item: " + id);
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
