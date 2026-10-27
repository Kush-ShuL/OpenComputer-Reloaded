package ocfixture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/** Real server recipe reload, HOCON includes, and datapack precedence. */
public final class RecipeConfigContract {
    private static Run active;
    private RecipeConfigContract() {}
    public static void start(MinecraftServer server, List<String> results) throws Exception {
        active = new Run(server, results);
        active.start();
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        if (active == null) return;
        try {
            if (active.pending.isDone()) { active.pending.join(); active.verifyReload(); active = null; }
        } catch (Throwable exception) { active.results.add("FAIL C17 recipe configuration: " + exception); active = null; }
    }
    public static void close() { active = null; }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Run {
        final MinecraftServer server;
        final List<String> results;
        final Path directory = Path.of(System.getProperty("ocfixture.deviceDirectory"));
        CompletableFuture<Void> pending;
        Run(MinecraftServer server, List<String> results) { this.server = server; this.results = results; }
        CraftingRecipe crafting(String name) {
            var id = ResourceLocation.parse("opencomputers:default/" + name);
            var found = server.getRecipeManager().byKey(id);
            require(found.isPresent(), "Missing " + id + "; resources=" + server.getResourceManager()
                    .getResourceStack(id.withPath(path -> "recipe/" + path + ".json")).stream().map(r -> r.sourcePackId()).toList());
            return (CraftingRecipe) found.get().value();
        }
        ItemStack craft(String name, ItemStack... ingredients) {
            CraftingInput input = CraftingInput.of(ingredients.length, 1, List.of(ingredients));
            CraftingRecipe recipe = crafting(name);
            require(recipe.matches(input, server.overworld()), "Configured ingredients did not match " + name);
            return recipe.assemble(input, server.registryAccess());
        }
        void start() throws Exception {
            ItemStack analyzer = craft("analyzer", new ItemStack(net.minecraft.world.item.Items.DIAMOND));
            require(analyzer.is(Items.get("analyzer").item()) && analyzer.getCount() == 1, "Included custom recipe output differs");
            require(craft("disk", new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT)).getCount() == 2, "Configured material output count differs");
            require(server.getRecipeManager().byKey(ResourceLocation.parse("opencomputers:default/manual")).isEmpty(), "true recipe was not disabled");
            require(server.getRecipeManager().byKey(ResourceLocation.parse("opencomputers:default/transistor")).isEmpty(), "false recipe was not disabled");
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(server.overworld().enabledFeatures(), true, server.registryAccess()));
            require(CreativeTab.instance.getDisplayItems().stream().anyMatch(s -> s.is(Items.get("manual").item())), "true disabled recipe hid its item");
            require(CreativeTab.instance.getDisplayItems().stream().noneMatch(s -> s.is(Items.get("transistor").item())), "false disabled recipe left its item visible");
            ItemStack bios = craft("luabios", Items.get("eeprom").createItemStack(1), new ItemStack(net.minecraft.world.item.Items.BOOK));
            require(!bios.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().isEmpty(), "Runtime Lua BIOS recipe lost its data operation");
            var furnace = server.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                    new SingleRecipeInput(new ItemStack(net.minecraft.world.item.Items.CHARCOAL)), server.overworld()).orElseThrow();
            require(furnace.value().getResultItem(server.registryAccess()).is(net.minecraft.world.item.Items.IRON_NUGGET), "Generic furnace recipe or legacy charcoal mapping failed");
            require(craft("wrench", new ItemStack(net.minecraft.world.item.Items.DIAMOND)).getCount() == 1, "Initial configured wrench recipe absent");
            String user = Files.readString(directory.resolve("config/opencomputers/user.recipes"), StandardCharsets.UTF_8);
            require(user.contains("include file(\"fixture.recipes\")"), "User recipe file was overwritten");
            Path custom = directory.resolve("config/opencomputers/fixture.recipes");
            Files.writeString(custom, Files.readString(custom).replace("output: 2", "output: 3"), StandardCharsets.UTF_8);
            Path pack = directory.resolve("ocfixture-device-world/datapacks/ocfixture-recipes");
            Files.createDirectories(pack.resolve("data/opencomputers/recipe/default"));
            Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"OC runtime recipe fixture\"}}");
            Files.writeString(pack.resolve("data/opencomputers/recipe/default/wrench.json"), "{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"misc\",\"ingredients\":[{\"item\":\"minecraft:emerald\"}],\"result\":{\"id\":\"opencomputers:wrench\"}}");
            Files.createDirectories(pack.resolve("data/opencomputers/recipe/default/generic"));
            Files.writeString(pack.resolve("data/opencomputers/recipe/default/generic/0.json"), "{\"type\":\"minecraft:smelting\",\"category\":\"misc\",\"ingredient\":{\"item\":\"minecraft:charcoal\"},\"result\":{\"id\":\"minecraft:gold_nugget\"},\"experience\":0.0,\"cookingtime\":200}");
            server.getPackRepository().reload();
            List<String> packs = new ArrayList<>(server.getPackRepository().getSelectedIds());
            require(server.getPackRepository().getAvailableIds().contains("file/ocfixture-recipes"), "Isolated recipe datapack was not discovered");
            packs.add("file/ocfixture-recipes");
            pending = server.reloadResources(packs);
            results.add("C17 initial include/custom/boolean visibility/furnace metadata/BIOS operations PASS");
        }
        void verifyReload() {
            require(craft("disk", new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT)).getCount() == 3, "Datapack reload did not re-read HOCON include");
            require(craft("wrench", new ItemStack(net.minecraft.world.item.Items.EMERALD)).getCount() == 1, "HOCON replaced a higher-priority datapack recipe");
            var furnace = server.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                    new SingleRecipeInput(new ItemStack(net.minecraft.world.item.Items.CHARCOAL)), server.overworld()).orElseThrow();
            require(furnace.value().getResultItem(server.registryAccess()).is(net.minecraft.world.item.Items.GOLD_NUGGET), "Datapack could not override a new generic recipe without a bundled JSON");
            results.add("C17 real reload re-read config; higher-priority datapack wins; user file retained PASS");
        }
    }
}
