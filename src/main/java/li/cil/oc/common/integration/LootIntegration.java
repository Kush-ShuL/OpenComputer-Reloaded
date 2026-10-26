package li.cil.oc.common.integration;

import com.mojang.serialization.MapCodec;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.common.init.Items;
import li.cil.oc.common.recipe.Recipes;
import li.cil.oc.server.fs.PathFileSystem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.SetCustomDataFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Program disk descriptors and the original two independent weighted selections.
 * Derived from MightyPirates/OpenComputers Loot.scala, MIT license.
 * The fixed 1.12 source commented out ChestGenHooks injection; this restores its
 * documented four containers using modern table entries and a global modifier.
 */
public final class LootIntegration {
    static final String PENDING = "oc:worldLoot";
    private static final Set<ResourceLocation> TABLES = Set.of(
            ResourceLocation.withDefaultNamespace("chests/simple_dungeon"),
            ResourceLocation.withDefaultNamespace("chests/desert_pyramid"),
            ResourceLocation.withDefaultNamespace("chests/jungle_temple"),
            ResourceLocation.withDefaultNamespace("chests/stronghold_library"));
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, OpenComputers.ID);
    private static final Map<MinecraftServer, WorldLoot> WORLDS = new WeakHashMap<>();
    private static final Set<LootPoolEntryContainer> PROGRAM_ENTRIES = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean registered;
    static { MODIFIERS.register("program_disks", () -> ProgramDiskLootModifier.CODEC); }
    private LootIntegration() {}

    public static synchronized void register(IEventBus bus) {
        if (registered) return;
        registered = true;
        MODIFIERS.register(bus);
        bus.addListener(LootIntegration::registerPayloads);
        NeoForge.EVENT_BUS.register(LootIntegration.class);
    }

    /** A real weight in an existing pool: pool conditions, rolls and other entries survive. */
    @SubscribeEvent public static synchronized void loadTable(LootTableLoadEvent event) {
        int weight = Settings.get().lootProbability;
        if (!TABLES.contains(event.getName())) return;
        // The public NeoForge API exposes pools by name, but has no pool/entry enumeration or entry setter.
        // Minimal ATs allow copying only the entries list. Never round-trip the source table through codecs:
        // its enchantment NamedSets belong to the reloadable registry owner, not event.getRegistries().
        List<LootPool> pools = event.getTable().pools;
        for (LootPool pool : pools) {
            if (pool.entries.stream().anyMatch(PROGRAM_ENTRIES::contains)) {
                if (pool.isFrozen()) throw new IllegalStateException("Program disk pool is already frozen: " + event.getName());
                pool.entries = pool.entries.stream().filter(entry -> !PROGRAM_ENTRIES.contains(entry)).toList();
            }
        }
        if (weight <= 0) return;
        LootPool chosen = null;
        for (LootPool pool : pools) {
            if (pool.entries.isEmpty()) continue;
            if (chosen == null) chosen = pool;
            if (containsMaterial(pool.entries)) { chosen = pool; break; }
        }
        if (chosen == null) return; // Respect intentionally empty datapack tables.
        if (chosen.isFrozen()) throw new IllegalStateException("Program disk pool is already frozen: " + event.getName());
        CompoundTag marker = new CompoundTag(); marker.putBoolean(PENDING, true);
        LootPoolEntryContainer entry = LootItem.lootTableItem(li.cil.oc.api.Items.get("floppy").item())
                .setWeight(weight).apply(SetCustomDataFunction.setCustomData(marker)).build();
        List<LootPoolEntryContainer> entries = new ArrayList<>(chosen.entries);
        entries.add(entry);
        chosen.entries = List.copyOf(entries);
        PROGRAM_ENTRIES.add(entry);
    }

    private static boolean containsMaterial(List<LootPoolEntryContainer> entries) {
        for (LootPoolEntryContainer entry : entries) {
            if (entry instanceof LootItem item && (item.item.value() == net.minecraft.world.item.Items.IRON_INGOT
                    || item.item.value() == net.minecraft.world.item.Items.GOLD_INGOT
                    || item.item.value() == net.minecraft.world.item.Items.DIAMOND)) return true;
            if (entry instanceof CompositeEntryBase composite && containsMaterial(composite.children)) return true;
        }
        return false;
    }

    @SubscribeEvent public static void loadWorld(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) {
            WorldLoot loot = load(level.getServer());
            synchronized (WORLDS) { WORLDS.put(level.getServer(), loot); }
            Recipes.setWorldLootDisks(level.registryAccess(), loot.externalDisks);
        }
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        synchronized (WORLDS) { WORLDS.remove(event.getServer()); }
        Recipes.setWorldLootDisks(event.getServer().registryAccess(), List.of());
    }

    @SubscribeEvent public static void unloadWorld(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) {
            synchronized (WORLDS) { WORLDS.remove(level.getServer()); }
            Recipes.setWorldLootDisks(level.registryAccess(), List.of());
        }
    }

    /** Additional creative templates for the current client connection; bundled disks are already in Items. */
    public static List<ItemStack> clientDisks() { return Recipes.clientWorldLootDisks(); }

    public static List<ItemStack> worldDisks(ServerLevel level) {
        synchronized (WORLDS) {
            WorldLoot loot = WORLDS.get(level.getServer());
            return loot == null ? List.of() : loot.externalDisks.stream().map(ItemStack::copy).toList();
        }
    }

    private record WorldLoot(List<Items.LootDisk> disks, Map<String, Path> externalFactories, List<ItemStack> externalDisks) {}

    private static WorldLoot load(MinecraftServer server) {
        List<Items.LootDisk> disks = new ArrayList<>();
        Map<String, Path> factories = new LinkedHashMap<>();
        List<ItemStack> externalDisks = new ArrayList<>();
        Path root = server.getWorldPath(LevelResource.ROOT).resolve(Settings.savePath).resolve("loot").toAbsolutePath().normalize();
        Path descriptor = root.resolve("loot.properties");
        if (Files.isRegularFile(descriptor)) {
            try (InputStream input = Files.newInputStream(descriptor)) {
                Properties entries = new Properties(); entries.load(input);
                for (String path : entries.stringPropertyNames().stream().sorted().toList()) {
                    String value = entries.getProperty(path);
                    try {
                        Path directory = root.resolve(path).normalize();
                        if (path.isBlank() || !directory.startsWith(root) || directory.equals(root) || Path.of(path).isAbsolute())
                            throw new IllegalArgumentException("Program disk directory escapes the world loot root");
                        String[] fields = value.split(":");
                        String label = fields.length == 2 || fields.length == 3 ? fields[0] : value;
                        int weight = fields.length == 2 || fields.length == 3 ? Integer.parseInt(fields[1]) : 1;
                        DyeColor color = fields.length == 3 ? dye(fields[2]) : DyeColor.LIGHT_GRAY;
                        ItemStack disk = li.cil.oc.api.Items.get("floppy").createItemStack(1);
                        CompoundTag data = new CompoundTag(); data.putString("oc:fs.label", path);
                        CompoundTag tag = new CompoundTag(); tag.put("oc:data", data);
                        tag.putString("oc:lootFactory", "opencomputers:" + path);
                        tag.putInt("oc:color", 15 - color.getId());
                        disk.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                        disk.set(DataComponents.CUSTOM_NAME, Component.literal(label));
                        disks.add(new Items.LootDisk(disk, Math.max(0, weight)));
                        externalDisks.add(disk.copy());
                        factories.put("opencomputers:" + path, directory);
                    } catch (RuntimeException error) {
                        OpenComputers.LOGGER.warn("Bad world loot descriptor {}={}", path, value, error);
                    }
                }
            } catch (Exception error) { OpenComputers.LOGGER.warn("Failed opening world loot descriptor {}", descriptor, error); }
        }
        // The source appends globals even for the same path unless the complete stack/weight pair matches.
        for (Items.LootDisk global : Items.INSTANCE.lootDisks()) {
            boolean duplicate = disks.stream().anyMatch(disk -> disk.weight() == global.weight()
                    && ItemStack.isSameItemSameComponents(disk.stack(), global.stack()));
            if (!duplicate) disks.add(global);
        }
        return new WorldLoot(List.copyOf(disks), Map.copyOf(factories), List.copyOf(externalDisks));
    }

    static ItemStack randomDisk(ServerLevel level, RandomSource random) {
        WorldLoot world;
        synchronized (WORLDS) { world = WORLDS.get(level.getServer()); }
        List<Items.LootDisk> disks = world == null ? Items.INSTANCE.lootDisks() : world.disks;
        long total = 0;
        for (Items.LootDisk disk : disks) total += disk.weight();
        if (total <= 0) return ItemStack.EMPTY;
        // Avoid the source's repeated-stack array and retain exact integer weights without overflow.
        long bits, choice;
        do { bits = random.nextLong() >>> 1; choice = bits % total; } while (bits - choice + total - 1 < 0);
        for (Items.LootDisk disk : disks) { choice -= disk.weight(); if (choice < 0) return disk.stack(); }
        throw new AssertionError("Program disk weights changed during selection");
    }

    /** Resolve using the actual host's world, allowing identical factory names in different saves. */
    public static Callable<li.cil.oc.api.fs.FileSystem> fileSystemFactory(String name, Level level) {
        if (level != null && !level.isClientSide && level.getServer() != null) {
            WorldLoot world;
            synchronized (WORLDS) { world = WORLDS.get(level.getServer()); }
            Path path = world == null ? null : world.externalFactories.get(name);
            if (path != null) return () -> {
                Files.createDirectories(path);
                return new PathFileSystem(path, true, 0);
            };
        }
        return Items.INSTANCE.fileSystemFactory(name);
    }

    private static DyeColor dye(String legacy) {
        return switch (legacy) {
            case "dyeWhite" -> DyeColor.WHITE; case "dyeOrange" -> DyeColor.ORANGE;
            case "dyeMagenta" -> DyeColor.MAGENTA; case "dyeLightBlue" -> DyeColor.LIGHT_BLUE;
            case "dyeYellow" -> DyeColor.YELLOW; case "dyeLime" -> DyeColor.LIME;
            case "dyePink" -> DyeColor.PINK; case "dyeGray" -> DyeColor.GRAY;
            case "dyeLightGray", "dyeSilver" -> DyeColor.LIGHT_GRAY; case "dyeCyan" -> DyeColor.CYAN;
            case "dyePurple" -> DyeColor.PURPLE; case "dyeBlue" -> DyeColor.BLUE;
            case "dyeBrown" -> DyeColor.BROWN; case "dyeGreen" -> DyeColor.GREEN;
            case "dyeRed" -> DyeColor.RED; case "dyeBlack" -> DyeColor.BLACK;
            default -> DyeColor.LIGHT_GRAY;
        };
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WorldLoot loot;
            synchronized (WORLDS) { loot = WORLDS.get(player.server); }
            PacketDistributor.sendToPlayer(player, new DiskSync(loot == null ? List.of() : loot.externalDisks));
        }
    }

    public record DiskSync(List<ItemStack> disks) implements CustomPacketPayload {
        public static final Type<DiskSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "loot_disks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DiskSync> CODEC = StreamCodec.of(
                (out, packet) -> { out.writeVarInt(packet.disks.size()); for (ItemStack disk : packet.disks) ItemStack.STREAM_CODEC.encode(out, disk); },
                in -> {
                    int count = in.readVarInt();
                    if (count < 0 || count > 4096) throw new IllegalArgumentException("Invalid program disk count");
                    List<ItemStack> disks = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) disks.add(ItemStack.STREAM_CODEC.decode(in));
                    return new DiskSync(List.copyOf(disks));
                });
        @Override public Type<DiskSync> type() { return TYPE; }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(DiskSync.TYPE, DiskSync.CODEC,
                (packet, context) -> context.enqueueWork(() -> li.cil.oc.client.LootClientLifecycle.receive(packet.disks, context.player())));
    }
}
