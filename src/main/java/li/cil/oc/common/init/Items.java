package li.cil.oc.common.init;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import li.cil.oc.Settings;
import li.cil.oc.api.detail.ItemAPI;
import li.cil.oc.api.detail.ItemInfo;
import li.cil.oc.api.fs.FileSystem;
import li.cil.oc.common.recipe.Recipes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModLoadingContext;
import li.cil.oc.common.item.ComponentItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

/**
 * The Items API catalog and original EEPROM/loot-filesystem factories.
 * Hardware registers a descriptor only after providing its actual item/block
 * implementation; this catalog never registers placeholder game content.
 */
public final class Items implements ItemAPI {
    public static final Items INSTANCE = new Items();
    private static final DeferredRegister<Item> GAME_ITEMS = DeferredRegister.create(Registries.ITEM, "opencomputers");
    private static final DeferredRegister<Block> GAME_BLOCKS = DeferredRegister.create(Registries.BLOCK, "opencomputers");
    private static boolean driversInitialized;
    static {
        for(String name : List.of("alu","arrowkeys","buttongroup","card","chip1","chip2","chip3","chipdiamond","circuitboard","cu","cuttingwire","disk","interweb","numpad","printedcircuitboard","rawcircuitboard","transistor"))
            material(name, li.cil.oc.common.item.CraftingMaterialItem.Kind.PLAIN,64);
        material("acid",li.cil.oc.common.item.CraftingMaterialItem.Kind.ACID,64);
        material("chamelium",li.cil.oc.common.item.CraftingMaterialItem.Kind.CHAMELIUM,64);
        material("inkcartridge",li.cil.oc.common.item.CraftingMaterialItem.Kind.INK,1);
        material("inkcartridgeempty",li.cil.oc.common.item.CraftingMaterialItem.Kind.PLAIN,1);
        var chameliumBlock=GAME_BLOCKS.register("chameliumblock",()->new li.cil.oc.common.block.ChameliumBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(2,5).requiresCorrectToolForDrops()));
        var chameliumItem=GAME_ITEMS.register("chameliumblock",()->new BlockItem(chameliumBlock.get(),new Item.Properties()));
        INSTANCE.registerBlock("chameliumblock",chameliumBlock::get,chameliumItem::get);
        for (int tier = 0; tier < 3; tier++) hardware("cpu" + (tier + 1), ComponentItem.Kind.PROCESSOR, tier);
        hardware("apu1", ComponentItem.Kind.APU, 0);
        hardware("apu2", ComponentItem.Kind.APU, 1);
        hardware("apucreative", ComponentItem.Kind.APU, 2);
        for (int level = 0; level < 6; level++) hardware("ram" + (level + 1), ComponentItem.Kind.MEMORY, level);
        hardware("eeprom", ComponentItem.Kind.EEPROM, 0);
        hardware("floppy", ComponentItem.Kind.FLOPPY, 0);
        for (int tier = 0; tier < 3; tier++) hardware("hdd" + (tier + 1), ComponentItem.Kind.HDD, tier);
        for (int tier = 0; tier < 3; tier++) hardware("graphicscard" + (tier + 1), ComponentItem.Kind.GRAPHICS, tier);
        hardware("lancard", ComponentItem.Kind.NETWORK, 0);
        var nanomachines = GAME_ITEMS.register("nanomachines", () -> new li.cil.oc.common.nanomachines.NanomachinesItem(new Item.Properties()));
        INSTANCE.registerItem("nanomachines", nanomachines::get);
        for (int tier = 0; tier < 2; tier++) hardware("wlancard" + (tier + 1), ComponentItem.Kind.WIRELESS, tier);
    }
    private final Map<String, ItemInfo> descriptors = new LinkedHashMap<>();
    private final Map<Item, ItemInfo> itemNames = new IdentityHashMap<>();
    private final Map<Block, ItemInfo> blockNames = new IdentityHashMap<>();
    private final List<BaseDescriptor> baseDescriptors = new ArrayList<>();
    private final List<ItemStack> extraCreativeItems = new ArrayList<>();
    private final Map<String, Callable<FileSystem>> floppyFactories = new LinkedHashMap<>();
    private final Map<String, LootDisk> lootDisks = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>(Map.of("datacard", "datacard1", "wlancard", "wlancard2"));

    public record LootDisk(ItemStack stack, int weight) {
        public LootDisk {
            if (stack.isEmpty()) throw new IllegalArgumentException("A loot disk cannot be empty");
            if (weight < 0) throw new IllegalArgumentException("A loot disk weight cannot be negative");
            stack = stack.copyWithCount(1);
        }
        @Override public ItemStack stack() { return stack.copy(); }
    }

    private Items() {}

    private static void material(String name,li.cil.oc.common.item.CraftingMaterialItem.Kind kind,int size) {
        var item=GAME_ITEMS.register(name,()->new li.cil.oc.common.item.CraftingMaterialItem(new Item.Properties().stacksTo(size),kind));
        INSTANCE.registerItem(name,item::get);
    }

    private static void hardware(String name, ComponentItem.Kind kind, int tier) {
        var holder = GAME_ITEMS.register(name, () -> new ComponentItem(new Item.Properties(), kind, tier));
        INSTANCE.registerItem(name, holder::get);
    }

    public static void register(IEventBus modBus) {
        GAME_BLOCKS.register(modBus);
        GAME_ITEMS.register(modBus);
        ComponentItem.registerNetworking(modBus);
    }

    public static synchronized void initializeDrivers() {
        if (driversInitialized) return;
        if (li.cil.oc.api.API.driver == null) throw new IllegalStateException("Initialize the driver API before hardware drivers");
        addDriver(new li.cil.oc.server.driver.item.Processor());
        addDriver(new li.cil.oc.server.driver.item.APU());
        addDriver(new li.cil.oc.server.driver.item.Memory());
        addDriver(new li.cil.oc.server.driver.item.EEPROM());
        addDriver(new li.cil.oc.server.driver.item.FileSystem());
        addDriver(new li.cil.oc.server.driver.item.GraphicsCard());
        addDriver(new li.cil.oc.server.driver.item.NetworkCard());
        driversInitialized = true;
    }

    private static void addDriver(li.cil.oc.api.driver.DriverItem driver) {
        li.cil.oc.api.Driver.add(driver);
        if (driver instanceof li.cil.oc.api.driver.EnvironmentProvider provider) li.cil.oc.api.Driver.add(provider);
    }

    @Override
    public synchronized ItemInfo get(String name) {
        if (name == null) return null;
        ItemInfo descriptor = descriptors.get(name);
        return descriptor != null ? descriptor : descriptors.get(aliases.get(name));
    }

    @Override
    public synchronized ItemInfo get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ItemInfo cached = stack.getItem() instanceof BlockItem blockItem ? blockNames.get(blockItem.getBlock()) : itemNames.get(stack.getItem());
        if (cached != null) return cached;
        // Deferred registers are resolved after catalog declaration, so reverse
        // lookups populate lazily rather than dereferencing suppliers too early.
        for (BaseDescriptor descriptor : baseDescriptors) {
            if (descriptor.stackItem() == stack.getItem()) {
                if (descriptor.blockSupplier != null) blockNames.put(descriptor.block(), descriptor);
                else itemNames.put(descriptor.stackItem(), descriptor);
                return descriptor;
            }
        }
        return null;
    }

    public synchronized ItemInfo registerItem(String name, Supplier<? extends Item> item) {
        return addBase(new BaseDescriptor(name, Objects.requireNonNull(item), null));
    }

    public synchronized <T extends Item> T registerItem(T item, String name) {
        registerItem(name, () -> item);
        return item;
    }

    public synchronized ItemInfo registerBlock(String name, Supplier<? extends Block> block, Supplier<? extends Item> blockItem) {
        return addBase(new BaseDescriptor(name, Objects.requireNonNull(blockItem), Objects.requireNonNull(block)));
    }

    public synchronized <T extends Block> T registerBlock(T block, String name) {
        registerBlock(name, () -> block, block::asItem);
        return block;
    }

    private ItemInfo addBase(BaseDescriptor descriptor) {
        requireName(descriptor.name);
        ItemInfo existing = descriptors.putIfAbsent(descriptor.name, descriptor);
        if (existing != null) throw new IllegalArgumentException("Duplicate OpenComputers item definition: " + descriptor.name);
        baseDescriptors.add(descriptor);
        return descriptor;
    }

    /** Register a data-bearing variant, such as Lua BIOS or the OpenOS disk. */
    public synchronized ItemStack registerStack(ItemStack stack, String name) {
        requireName(name);
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Cannot register an empty item variant: " + name);
        ItemStack template = stack.copy();
        ItemInfo descriptor = new ItemInfo() {
            @Override public String name() { return name; }
            @Override public Block block() { return null; }
            @Override public Item item() { return template.getItem(); }
            @Override public ItemStack createItemStack(int size) { return size <= 0 ? ItemStack.EMPTY : template.copyWithCount(size); }
        };
        if (descriptors.putIfAbsent(name, descriptor) != null) throw new IllegalArgumentException("Duplicate OpenComputers item variant: " + name);
        return template.copy();
    }

    public synchronized void registerAlias(String alias, String target) {
        requireName(alias); requireName(target);
        if (descriptors.containsKey(alias) || aliases.putIfAbsent(alias, target) != null)
            throw new IllegalArgumentException("Duplicate OpenComputers item alias: " + alias);
    }

    public synchronized List<ItemInfo> definitions() { return List.copyOf(descriptors.values()); }

    public synchronized List<ItemStack> registeredItems() {
        List<ItemStack> hardware = new ArrayList<>();
        List<ItemStack> other = new ArrayList<>();
        for (BaseDescriptor descriptor : baseDescriptors) {
            ItemStack stack = descriptor.createItemStack(1);
            (stack.getItem() instanceof ComponentItem ? hardware : other).add(stack);
        }
        hardware.addAll(other);
        extraCreativeItems.forEach(stack -> hardware.add(stack.copy()));
        return List.copyOf(hardware);
    }

    public synchronized Callable<FileSystem> fileSystemFactory(String factory) {
        return floppyFactories.get(factory);
    }

    public synchronized Map<String, Callable<FileSystem>> fileSystemFactories() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(floppyFactories));
    }

    @Override
    public ItemStack registerFloppy(String name, DyeColor color, Callable<FileSystem> factory, boolean doRecipeCycling) {
        return registerFloppy(ModLoadingContext.get().getActiveNamespace(), name, color, factory, doRecipeCycling);
    }

    /** Explicit ownership is useful for registrations performed after mod setup. */
    public synchronized ItemStack registerFloppy(String namespace, String name, DyeColor color,
                                                Callable<FileSystem> factory, boolean doRecipeCycling) {
        requireName(namespace); requireName(name);
        Objects.requireNonNull(factory, "Loot filesystem factory");
        Objects.requireNonNull(color, "Loot disk color");
        ItemStack stack = require("floppy").createItemStack(1);
        String factoryName = namespace + ':' + name;
        if (floppyFactories.putIfAbsent(factoryName, factory) != null)
            throw new IllegalArgumentException("Duplicate loot filesystem factory: " + factoryName);
        CompoundTag data = new CompoundTag();
        data.putString("oc:fs.label", name);
        CompoundTag root = new CompoundTag();
        root.put("oc:data", data);
        root.putString("oc:lootFactory", factoryName);
        // Legacy oc:color stores dye damage, the reverse of modern getId().
        root.putInt("oc:color", 15 - color.getId());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        extraCreativeItems.add(stack.copy());
        Recipes.registerLootDisk(stack, doRecipeCycling);
        return stack.copy();
    }

    @Override
    public synchronized ItemStack registerEEPROM(String name, byte[] code, byte[] data, boolean readonly) {
        CompoundTag payload = new CompoundTag();
        if (name != null) payload.putString("oc:label", name.trim().substring(0, Math.min(24, name.trim().length())));
        if (code != null) payload.putByteArray("oc:eeprom", Arrays.copyOf(code, Math.min(code.length, Settings.get().eepromSize)));
        if (data != null) payload.putByteArray("oc:userdata", Arrays.copyOf(data, Math.min(data.length, Settings.get().eepromDataSize)));
        payload.putBoolean("oc:readonly", readonly);
        CompoundTag root = new CompoundTag();
        root.put("oc:data", payload);
        ItemStack stack = require("eeprom").createItemStack(1);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        extraCreativeItems.add(stack.copy());
        return stack.copy();
    }

    /** Called after the real EEPROM item is registered. */
    public synchronized ItemStack initializeLuaBios() {
        ItemInfo existing = descriptors.get("luabios");
        if (existing != null) return existing.createItemStack(1);
        try (InputStream stream = Items.class.getResourceAsStream("/assets/opencomputers/lua/bios.lua")) {
            if (stream == null) throw new IllegalStateException("The original OpenComputers Lua BIOS resource is missing");
            ItemStack bios = registerEEPROM("EEPROM (Lua BIOS)", stream.readAllBytes(), null, false);
            return registerStack(bios, "luabios");
        } catch (IOException error) { throw new IllegalStateException("Could not read the original Lua BIOS", error); }
    }

    /** Called after the real floppy item and filesystem API are registered. */
    public synchronized void initializeBundledLootDisks() {
        Properties properties = new Properties();
        try (InputStream stream = Items.class.getResourceAsStream("/assets/opencomputers/loot/loot.properties")) {
            if (stream == null) throw new IllegalStateException("The original OpenComputers loot disk list is missing");
            properties.load(stream);
        } catch (IOException error) { throw new IllegalStateException("Could not read the original loot disk list", error); }
        // Sort to produce the same cycling order on dedicated servers/clients.
        for (String path : properties.stringPropertyNames().stream().sorted().toList()) {
            String factoryName = "opencomputers:" + path;
            if (lootDisks.containsKey(factoryName)) continue;
            String[] fields = properties.getProperty(path).split(":");
            String label = fields[0];
            int weight = fields.length >= 2 ? Integer.parseInt(fields[1]) : 1;
            DyeColor color = fields.length >= 3 ? dyeByLegacyName(fields[2]) : DyeColor.LIGHT_GRAY;
            ItemStack disk = registerFloppy("opencomputers", path, color, () -> {
                FileSystem fileSystem = li.cil.oc.api.FileSystem.fromClass(Items.class, "opencomputers", "loot/" + path);
                if (fileSystem == null) throw new IllegalStateException("Filesystem API is not initialized for loot disk " + path);
                return fileSystem;
            }, true);
            disk.set(DataComponents.CUSTOM_NAME, Component.literal(label));
            for (int index = 0; index < extraCreativeItems.size(); index++) {
                ItemStack old = extraCreativeItems.get(index);
                String oldFactory = old.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("oc:lootFactory");
                if (factoryName.equals(oldFactory)) extraCreativeItems.set(index, disk.copy());
            }
            registerStack(disk, path);
            lootDisks.put(factoryName, new LootDisk(disk, weight));
            // Update the cycling copy after applying the player's disk label.
            Recipes.registerLootDisk(disk, true);
        }
    }

    public synchronized List<LootDisk> lootDisks() { return List.copyOf(lootDisks.values()); }

    public synchronized ItemStack randomLootDisk(RandomSource random) {
        int total = lootDisks.values().stream().mapToInt(LootDisk::weight).sum();
        if (total <= 0) return ItemStack.EMPTY;
        int choice = random.nextInt(total);
        for (LootDisk disk : lootDisks.values()) {
            choice -= disk.weight();
            if (choice < 0) return disk.stack();
        }
        throw new AssertionError("Loot disk weights changed during selection");
    }

    private ItemInfo require(String name) {
        ItemInfo descriptor = get(name);
        if (descriptor == null) throw new IllegalStateException("The actual OpenComputers item has not been implemented/registered: " + name);
        return descriptor;
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("OpenComputers item names must not be empty");
    }

    private static DyeColor dyeByLegacyName(String name) {
        return switch (name) {
            case "dyeBlack" -> DyeColor.BLACK; case "dyeRed" -> DyeColor.RED;
            case "dyeGreen" -> DyeColor.GREEN; case "dyeBrown" -> DyeColor.BROWN;
            case "dyeBlue" -> DyeColor.BLUE; case "dyePurple" -> DyeColor.PURPLE;
            case "dyeCyan" -> DyeColor.CYAN; case "dyeLightGray" -> DyeColor.LIGHT_GRAY;
            case "dyeGray" -> DyeColor.GRAY; case "dyePink" -> DyeColor.PINK;
            case "dyeLime" -> DyeColor.LIME; case "dyeYellow" -> DyeColor.YELLOW;
            case "dyeLightBlue" -> DyeColor.LIGHT_BLUE; case "dyeMagenta" -> DyeColor.MAGENTA;
            case "dyeOrange" -> DyeColor.ORANGE; case "dyeWhite" -> DyeColor.WHITE;
            default -> throw new IllegalArgumentException("Unknown original loot disk dye: " + name);
        };
    }

    private static final class BaseDescriptor implements ItemInfo {
        private final String name;
        private final Supplier<? extends Item> itemSupplier;
        private final Supplier<? extends Block> blockSupplier;
        private BaseDescriptor(String name, Supplier<? extends Item> item, Supplier<? extends Block> block) {
            this.name = name; itemSupplier = item; blockSupplier = block;
        }
        private Item stackItem() {
            Item item = Objects.requireNonNull(itemSupplier.get(), "OpenComputers item supplier: " + name);
            if (item == net.minecraft.world.item.Items.AIR)
                throw new IllegalStateException("Actual block item has not been registered for OpenComputers " + name);
            return item;
        }
        @Override public String name() { return name; }
        @Override public Block block() { return blockSupplier == null ? null : Objects.requireNonNull(blockSupplier.get()); }
        @Override public Item item() { return blockSupplier == null ? stackItem() : null; }
        @Override public ItemStack createItemStack(int size) { return size <= 0 ? ItemStack.EMPTY : new ItemStack(stackItem(), size); }
    }
}
