package ocfixture;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Actual adapter-discovered vanilla blocks and one finite configured-crafting reload. */
public final class RecipeVanillaContract {
    private static Run active;
    private static boolean observing;
    private RecipeVanillaContract() {}

    public static void start(ServerLevel level, BlockPos position, List<String> results) {
        require(active == null, "A recipe/vanilla contract is already running");
        if (!observing) { observing = true; NeoForge.EVENT_BUS.addListener(RecipeVanillaContract::interact); }
        Run run = new Run(level, position.immutable(), results);
        active = run;
        try { run.start(); }
        catch (Throwable error) { run.fail(error); active = null; run.cleanup(); }
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        Run run = active;
        if (run == null) return;
        try { if (!run.tick()) return; }
        catch (Throwable error) { run.fail(error); }
        active = null;
        run.cleanup();
    }
    public static void close() {
        Run run = active; active = null;
        if (run != null) { run.results.add("FAIL recipe.vanilla: server stopped during the finite contract"); run.cleanup(); }
    }
    private static void interact(PlayerInteractEvent.RightClickBlock event) {
        Run run = active;
        if (run != null && event.getLevel() == run.level && event.getPos().equals(run.target)) {
            run.interactions++;
            event.setCanceled(true);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static String text(Object value) { return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(value); }
    private static Object first(Object[] values) { return values == null || values.length == 0 ? null : values[0]; }
    private static CompoundTag data(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    private static ItemStack item(String name) { var info = li.cil.oc.api.Items.get(name); require(info != null, "Missing real item " + name); return info.createItemStack(1); }

    private static final class Run {
        final ServerLevel level;
        final BlockPos position, target, commandResult;
        final List<String> results;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final Path user = Path.of(System.getProperty("ocfixture.deviceDirectory")).resolve("config/opencomputers/user.recipes");
        final boolean recipesOnly = "recipes".equals(System.getProperty("ocfixture.completionFocus", "all"));
        CompletableFuture<Void> reload;
        String original;
        String waiting = "initializing the real fixture";
        Case host;
        FakePlayer actor;
        int phase, ticks, since, interactions;
        boolean cleaned;

        Run(ServerLevel level, BlockPos position, List<String> results) {
            this.level = level; this.position = position; this.results = results;
            target = position.east(3); commandResult = position.south(3);
        }
        void remember(BlockPos pos) {
            if (restored.containsKey(pos)) return;
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) forced.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            level.getChunkAt(pos);
            require(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null, "Refusing occupied recipe/vanilla cell " + pos);
            restored.put(pos.immutable(), level.getBlockState(pos));
        }
        void place(BlockPos pos, BlockState state) {
            remember(pos);
            if (level.getBlockEntity(pos) instanceof Container container) container.clearContent();
            require(level.setBlock(pos, state, 3), "Could not place actual vanilla fixture block at " + pos);
        }
        void start() throws Exception {
            require(API.config.getStringList("integration.modBlacklist").stream().noneMatch(s -> s.trim().equalsIgnoreCase("minecraft")), "This finite enabled-driver scenario requires the actual minecraft integration");
            require(API.config.getBoolean("integration.vanilla.enableInventoryDriver"), "Inventory cancellation proof requires integration.vanilla.enableInventoryDriver=true before startup; the original default is false and otherwise no inventory driver is registered");
            place(position, li.cil.oc.api.Items.get("case1").block().defaultBlockState());
            place(position.east(), li.cil.oc.api.Items.get("cable").block().defaultBlockState());
            place(position.east(2), li.cil.oc.api.Items.get("adapter").block().defaultBlockState());
            require(level.getBlockEntity(position) instanceof Case, "Actual Case SDK marker unavailable");
            host = (Case) level.getBlockEntity(position);
            actor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "OCVanillaRecipe"));
            actor.setPos(position.getX() + .5, position.getY(), position.getZ() + 2.5);
            place(target.below(), Blocks.STONE.defaultBlockState());
            if (recipesOnly) {
                place(target, Blocks.CHEST.defaultBlockState());
                phase = 9;
                results.add("REUSE I12.vanilla-drivers/command/inventory-protection: completion-20261027-h1 and completion-20261027-h2 already passed these actual paths; recipes focus only checks the remaining configured recipes");
            } else {
                place(target, Blocks.FURNACE.defaultBlockState());
                Container furnace = (Container) level.getBlockEntity(target);
                furnace.setItem(0, new ItemStack(Items.IRON_ORE));
                furnace.setItem(1, new ItemStack(Items.COAL));
            }
            original = Files.readString(user, StandardCharsets.UTF_8);
            Files.writeString(user, original + """

                # Finite isolated fixture overrides; the original file is restored on completion.
                luabios { type: shapeless, input: ["minecraft:book"], output: 3 }
                lootdisks = [{ name: "opencomputers:openos", type: shapeless, input: ["minecraft:paper"], output: 2 }]
                generic = [
                  { result: "opencomputers:floppy", type: shapeless, input: ["opencomputers:floppy", "opencomputers:floppy"], output: 2 },
                  { result: "opencomputers:floppy", type: shapeless, input: ["opencomputers:floppy"] },
                  { result: "opencomputers:eeprom", type: shapeless, input: ["opencomputers:eeprom", "opencomputers:eeprom"], output: 2 },
                  { result: "opencomputers:navigationupgrade", type: shapeless, input: ["minecraft:filled_map", "minecraft:filled_map"] },
                  { result: "opencomputers:disk", type: shapeless, input: ["minecraft:iron_ingot", "minecraft:water_bucket"] }
                ]
                """, StandardCharsets.UTF_8);
            reload = level.getServer().reloadResources(level.getServer().getPackRepository().getSelectedIds());
        }
        Component component(String name, ItemStack blockItem) {
            if (host.machine().node().network() == null) { waiting = "the real Case node to join its physical network"; return null; }
            Set<Class<?>> types = Driver.environmentsFor(blockItem);
            require(types != null && !types.isEmpty(), "No registered item environment provider for " + blockItem);
            for (var node : host.machine().node().network().nodes())
                if (node instanceof Component component && component.name().equals(name) && types.contains(node.host().getClass())) return component;
            waiting = "Adapter-discovered " + name + " with registered provider type " + types;
            return null;
        }
        Object[] call(Component component, String name, Object... arguments) throws Exception { return component.invoke(name, host.machine(), arguments); }
        int number(Component component, String name) throws Exception { Object value = first(call(component, name)); require(value instanceof Number, "No numeric result from actual " + name + ": " + value); return ((Number) value).intValue(); }
        void next(BlockState state) { place(target, state); phase++; since = ticks; }
        boolean tick() throws Exception {
            if (++ticks > 400) throw new AssertionError("Recipe/vanilla contract timed out: " + diagnostic());
            if (!reload.isDone()) { waiting = "the one actual HOCON reload future"; return false; }
            reload.join();
            if (ticks - since < 5) { waiting = "five real world ticks after replacing the target; elapsed=" + (ticks - since); return false; }
            if (phase == 0) {
                Component furnace = component("furnace", new ItemStack(Items.FURNACE));
                if (furnace == null) return false;
                CompoundTag saved = level.getBlockEntity(target).saveWithoutMetadata(level.registryAccess());
                if (saved.getInt("BurnTime") <= 0 || saved.getInt("CookTime") <= 0) { waiting = "actual furnace BurnTime/CookTime > 0; observed=" + saved.getInt("BurnTime") + "/" + saved.getInt("CookTime"); return false; }
                require(number(furnace, "getBurnTime") == saved.getInt("BurnTime") && number(furnace, "getCookTime") == saved.getInt("CookTime")
                        && number(furnace, "getTotalCookTime") == saved.getInt("CookTimeTotal"), "Furnace driver differs from the actual burning/cooking fields");
                require(number(furnace, "getCurrentItemBurnTime") == new ItemStack(Items.COAL).getBurnTime(RecipeType.SMELTING)
                        && Boolean.TRUE.equals(first(call(furnace, "isBurning"))), "Actual furnace fuel duration/burning callback failed");
                next(Blocks.BREWING_STAND.defaultBlockState());
                Container brewing = (Container) level.getBlockEntity(target);
                brewing.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
                brewing.setItem(3, new ItemStack(Items.NETHER_WART));
                brewing.setItem(4, new ItemStack(Items.BLAZE_POWDER));
                return false;
            }
            if (phase == 1) {
                Component brewing = component("brewing_stand", new ItemStack(Items.BREWING_STAND));
                if (brewing == null) return false;
                int remaining = level.getBlockEntity(target).saveWithoutMetadata(level.registryAccess()).getShort("BrewTime");
                if (remaining <= 0) { waiting = "actual active brewing timer > 0; BrewTime=" + remaining; return false; }
                require(number(brewing, "getBrewTime") == remaining, "Brewing driver lost the real active brew timer");
                next(Blocks.COMPARATOR.defaultBlockState());
                place(target.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                return false;
            }
            if (phase == 2) {
                Component comparator = component("comparator", new ItemStack(Items.COMPARATOR));
                if (comparator == null) return false;
                int output = ((ComparatorBlockEntity) level.getBlockEntity(target)).getOutputSignal();
                if (output == 0) { waiting = "actual powered comparator output > 0; output=" + output; return false; }
                require(output == 15 && number(comparator, "getOutputSignal") == output, "Comparator driver differs from its actual powered block entity");
                place(target.north(), Blocks.AIR.defaultBlockState());
                next(Blocks.SPAWNER.defaultBlockState());
                ((SpawnerBlockEntity) level.getBlockEntity(target)).setEntityId(EntityType.PIG, level.random);
                return false;
            }
            if (phase == 3) {
                Component spawner = component("mob_spawner", new ItemStack(Items.SPAWNER));
                if (spawner == null) return false;
                require("minecraft:pig".equals(text(first(call(spawner, "getSpawningMobName")))), "Spawner callback did not report its actual configured entity");
                next(Blocks.NOTE_BLOCK.defaultBlockState());
                return false;
            }
            if (phase == 4) {
                Component note = component("note_block", new ItemStack(Items.NOTE_BLOCK));
                if (note == null) return false;
                level.setBlock(target, level.getBlockState(target).setValue(NoteBlock.NOTE, 19), 3);
                require(note.annotation("getPitch").direct() && number(note, "getPitch") == 20, "Direct pitch read retained a stale pre-tick cache");
                rejectedPitch(note, 0); rejectedPitch(note, 26);
                require(Boolean.TRUE.equals(first(call(note, "trigger", 25))) && number(note, "getPitch") == 25, "Actual open-air note trigger/pitch failed");
                place(target.above(), Blocks.STONE.defaultBlockState());
                require(Boolean.FALSE.equals(first(call(note, "trigger"))), "Covered note block triggered despite original air-above restriction");
                place(target.above(), Blocks.AIR.defaultBlockState());
                next(Blocks.JUKEBOX.defaultBlockState());
                ((JukeboxBlockEntity) level.getBlockEntity(target)).setTheItem(new ItemStack(Items.MUSIC_DISC_13));
                return false;
            }
            if (phase == 5) {
                Component jukebox = component("jukebox", new ItemStack(Items.JUKEBOX));
                if (jukebox == null) return false;
                JukeboxBlockEntity tile = (JukeboxBlockEntity) level.getBlockEntity(target);
                String title = JukeboxSong.fromStack(level.registryAccess(), tile.getTheItem()).orElseThrow().value().description().getString();
                require(title.equals(text(first(call(jukebox, "getRecord")))), "Record title did not come from its real inserted song");
                call(jukebox, "stop"); require(!tile.getSongPlayer().isPlaying(), "Actual record stop failed");
                require(Boolean.TRUE.equals(first(call(jukebox, "play"))) && tile.getSongPlayer().isPlaying(), "Actual record playback failed");
                call(jukebox, "stop"); tile.setTheItem(ItemStack.EMPTY);
                require(first(call(jukebox, "getRecord")) == null && first(call(jukebox, "play")) == null, "Empty jukebox invented a record or success");
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) place(target.offset(x, -1, z), Blocks.IRON_BLOCK.defaultBlockState());
                next(Blocks.BEACON.defaultBlockState());
                return false;
            }
            if (phase == 6) {
                Component beacon = component("beacon", new ItemStack(Items.BEACON));
                if (beacon == null) return false;
                BeaconBlockEntity tile = (BeaconBlockEntity) level.getBlockEntity(target);
                int levels = tile.saveWithoutMetadata(level.registryAccess()).getInt("Levels");
                if (levels != 1) { waiting = "actual beacon pyramid Levels=1; observed=" + levels; return false; }
                require(number(beacon, "getLevels") == 1 && first(call(beacon, "getPrimaryEffect")) == null && first(call(beacon, "getSecondaryEffect")) == null, "Actual beacon pyramid/default effects differed");
                BeaconMenu menu = (BeaconMenu) tile.createMenu(74, actor.getInventory(), actor);
                require(menu != null, "Actual beacon menu unavailable");
                menu.getSlot(0).set(new ItemStack(Items.EMERALD));
                menu.updateEffects(Optional.of(MobEffects.MOVEMENT_SPEED), Optional.empty());
                require(menu.getSlot(0).getItem().isEmpty() && MobEffects.MOVEMENT_SPEED.value().getDescriptionId().equals(text(first(call(beacon, "getPrimaryEffect")))), "Real paid beacon selection did not reach its primary-effect callback");
                results.add("PASS I12.vanilla-drivers actual Case/cable/Adapter discovers furnace item environment and active timers, real brewing/comparator/spawner, same-tick note pitch/validation/covered trigger, real song playback and a paid level-one beacon effect without invented right-click authorization");
                if (API.config.getBoolean("integration.vanilla.enableCommandBlockDriver")) next(Blocks.COMMAND_BLOCK.defaultBlockState());
                else { results.add("LIMIT I12.command: original driver switch was disabled; real command execution was not exercised"); next(Blocks.CHEST.defaultBlockState()); phase = 8; }
                return false;
            }
            if (phase == 7) {
                Component command = component("command_block", new ItemStack(Items.COMMAND_BLOCK));
                if (command == null) return false;
                remember(commandResult);
                String instruction = "setblock " + commandResult.getX() + " " + commandResult.getY() + " " + commandResult.getZ() + " minecraft:gold_block";
                require(Boolean.TRUE.equals(first(call(command, "setCommand", instruction))) && instruction.equals(text(first(call(command, "getCommand")))), "Actual command block did not retain its command");
                Object[] executed = call(command, "executeCommand");
                if (level.getServer().isCommandBlockEnabled()) {
                    require(first(executed) instanceof Number && ((Number) executed[0]).intValue() == 1 && level.getBlockState(commandResult).is(Blocks.GOLD_BLOCK)
                            && executed.length == 2 && !text(executed[1]).isBlank(), "Actual command execution did not return its real success/output and world change");
                    results.add("PASS I12.command actual Adapter command callbacks execute a real setblock under the enabled server switch and return its success count/last output");
                } else require(first(executed) == null && executed.length == 2 && "command blocks are disabled".equals(text(executed[1])), "Disabled server command switch was ignored");
                next(Blocks.CHEST.defaultBlockState());
                return false;
            }
            if (phase == 8) {
                require(interactions == 0, "A special-purpose vanilla callback still emitted invented inventory protection events");
                Component inventory = null;
                for (var node : host.machine().node().network().nodes()) if (node instanceof Component component && component.name().equals("inventory") && component.methods().contains("getInventorySize")) { inventory = component; break; }
                if (inventory == null) { waiting = "Adapter chest inventory/getInventorySize; enableInventoryDriver=" + API.config.getBoolean("integration.vanilla.enableInventoryDriver"); return false; }
                Object[] denied = call(inventory, "getInventorySize");
                require(first(denied) == null && denied.length == 2 && "permission denied".equals(text(denied[1])) && interactions == 1, "Original inventory protection no longer refuses a canceled actual interaction");
                results.add("PASS I12.inventory-protection the actual chest inventory driver still posts its public interaction event and refuses canceled access");
                phase = 9;
            }
            if (phase == 9) {
                recipes();
                results.add("LIMIT recipe.vanilla: reuses earlier HOCON/include/reload/blacklist and assembler/disassembler/container-return evidence; no invented GT machine target, client visuals, remote input, multiplayer or extra architecture matrix is claimed");
                return true;
            }
            throw new AssertionError("Unexpected recipe/vanilla phase " + phase);
        }
        void rejectedPitch(Component note, int value) throws Exception {
            try { call(note, "setPitch", value); }
            catch (IllegalArgumentException error) { require("invalid pitch".equals(error.getMessage()), "Changed original note pitch error"); return; }
            throw new AssertionError("Note block accepted pitch " + value);
        }
        CraftingRecipe recipe(String path) {
            var id = ResourceLocation.fromNamespaceAndPath("opencomputers", "default/" + path);
            var holder = level.getRecipeManager().byKey(id).orElseThrow(() -> new AssertionError("Actual reload omitted " + id));
            require(holder.value() instanceof CraftingRecipe, "Actual configured recipe is not crafting: " + id);
            return (CraftingRecipe) holder.value();
        }
        ItemStack craft(String path, ItemStack... items) {
            CraftingRecipe recipe = recipe(path);
            CraftingInput input = CraftingInput.of(items.length, 1, List.of(items));
            require(recipe.matches(input, level), "Actual configured recipe does not match " + path + ": " + recipeDiagnostic(path, recipe, input));
            ItemStack result = recipe.assemble(input, level.registryAccess());
            require(!result.isEmpty(), "Actual configured recipe returned no output " + path);
            return result;
        }
        String recipeDiagnostic(String path, CraftingRecipe recipe, CraftingInput input) {
            var id = ResourceLocation.fromNamespaceAndPath("opencomputers", "default/" + path);
            List<List<String>> ingredients = recipe.getIngredients().stream().map(ingredient -> java.util.Arrays.stream(ingredient.getItems()).map(ItemStack::toString).toList()).toList();
            var packs = level.getServer().getResourceManager().getResourceStack(id.withPath(value -> "recipe/" + value + ".json")).stream().map(resource -> resource.sourcePackId()).toList();
            return "class=" + recipe.getClass().getName() + ", output=" + recipe.getResultItem(level.registryAccess())
                    + ", input=" + input.items() + ", ingredientCount=" + input.ingredientCount() + ", ingredients=" + ingredients
                    + ", canFit1x1=" + recipe.canCraftInDimensions(1, 1) + ", resourcePacks=" + packs;
        }
        void recipes() {
            ItemStack bios = craft("luabios", new ItemStack(Items.BOOK));
            require(bios.getCount() == 3 && data(bios).getCompound("oc:data").getByteArray("oc:eeprom").length > 0, "BIOS factory reset the configured count or lost real program bytes");
            ItemStack program = craft("loot/openos", new ItemStack(Items.PAPER));
            require(program.getCount() == 2 && "opencomputers:openos".equals(data(program).getString("oc:lootFactory")), "Program-disk factory reset the configured count or lost its real filesystem");

            ItemStack first = item("floppy"), second = item("floppy");
            CompoundTag a = new CompoundTag(), b = new CompoundTag();
            a.putString("copy_a", "first"); a.putString("shared", "first"); b.putInt("copy_b", 42); b.putString("shared", "second");
            first.set(DataComponents.CUSTOM_DATA, CustomData.of(a)); second.set(DataComponents.CUSTOM_DATA, CustomData.of(b));
            ItemStack copied = craft("generic/0", first, second);
            require(copied.getCount() == 2 && "first".equals(data(copied).getString("copy_a")) && data(copied).getInt("copy_b") == 42 && "first".equals(data(copied).getString("shared"))
                    && !data(first).contains("copy_b"), "Configured floppy copy did not merge missing source data without mutating inputs");
            a.putInt("oc:color", 1); first.set(DataComponents.CUSTOM_DATA, CustomData.of(a));
            ItemStack formatted = craft("generic/1", first);
            require(data(formatted).getAllKeys().equals(Set.of("oc:color")) && data(formatted).getInt("oc:color") == 1, "Formatting retained disk data or lost its explicit non-default color");
            a.putInt("oc:color", 7); first.set(DataComponents.CUSTOM_DATA, CustomData.of(a));
            require(data(craft("generic/1", first)).isEmpty(), "Formatting persisted the original default light-gray color");

            ItemStack rom = item("luabios"); CompoundTag root = data(rom), environment = root.getCompound("oc:data"), node = environment.getCompound("node");
            node.putString("address", UUID.randomUUID().toString()); environment.put("node", node); root.put("oc:data", environment); rom.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
            ItemStack copiedRom = craft("generic/2", rom, item("eeprom"));
            require(copiedRom.getCount() == 2 && java.util.Arrays.equals(data(copiedRom).getCompound("oc:data").getByteArray("oc:eeprom"), environment.getByteArray("oc:eeprom"))
                    && !data(copiedRom).getCompound("oc:data").getCompound("node").contains("address") && data(rom).getCompound("oc:data").getCompound("node").contains("address"), "Configured EEPROM copy lost bytes, retained address or mutated its source");

            ItemStack mapA = MapItem.create(level, position.getX(), position.getZ(), (byte) 0, true, false);
            ItemStack mapB = MapItem.create(level, position.getX() + 128, position.getZ(), (byte) 0, true, false);
            ItemStack navigation = craft("generic/3", mapA, mapB);
            var driver = Driver.driverFor(navigation); require(driver != null, "Real navigation driver absent");
            ItemStack selectedMap = ItemStack.parseOptional(level.registryAccess(), driver.dataTag(navigation).getCompound("oc:map"));
            require(mapB.get(DataComponents.MAP_ID).equals(selectedMap.get(DataComponents.MAP_ID)), "Configured navigation recipe did not preserve the last actual input map");

            ItemStack iron = new ItemStack(Items.IRON_INGOT), water = new ItemStack(Items.WATER_BUCKET);
            ItemStack material = craft("generic/4", iron, water);
            var remainders = recipe("generic/4").getRemainingItems(CraftingInput.of(2, 1, List.of(iron, water)));
            require(material.is(item("disk").getItem()) && remainders.get(0).isEmpty() && remainders.get(1).is(Items.BUCKET) && remainders.get(1).getCount() == 1
                    && iron.getCount() == 1 && water.is(Items.WATER_BUCKET), "Extended configured recipe lost its real bucket remainder or mutated recipe-preview inputs");
            results.add("PASS C17.configured-data one actual isolated reload preserves BIOS/program-disk output counts, merges floppy data, formats explicit/default colors, copies EEPROM bytes without address, saves the last real map and delegates the actual water-bucket remainder");
        }
        void fail(Throwable error) { results.add("FAIL recipe.vanilla phase=" + phase + " ticks=" + ticks + ": " + error); }
        String diagnostic() {
            List<String> nodes = new ArrayList<>();
            if (host != null && host.machine().node().network() != null)
                for (var node : host.machine().node().network().nodes()) if (node instanceof Component component)
                    nodes.add(component.name() + ":" + node.host().getClass().getName() + ":getInventorySize=" + component.methods().contains("getInventorySize"));
            return "phase=" + phase + ", waiting=" + waiting + ", target=" + level.getBlockState(target)
                    + ", inventoryEnabled=" + API.config.getBoolean("integration.vanilla.enableInventoryDriver")
                    + ", interactions=" + interactions + ", reloadDone=" + (reload != null && reload.isDone()) + ", nodes=" + nodes;
        }
        void cleanup() {
            if (cleaned) return; cleaned = true;
            if (host != null && host.machine() != null) host.machine().stop();
            var entries = new ArrayList<>(restored.entrySet()); Collections.reverse(entries);
            for (var entry : entries) { if (level.getBlockEntity(entry.getKey()) instanceof Container container) container.clearContent(); level.setBlock(entry.getKey(), entry.getValue(), 3); }
            if (actor != null) actor.discard();
            for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            if (original != null) try { Files.writeString(user, original, StandardCharsets.UTF_8); }
            catch (Exception error) { results.add("FAIL recipe.vanilla: restoring isolated user.recipes failed: " + error); }
        }
    }
}
