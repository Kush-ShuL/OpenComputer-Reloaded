package li.cil.oc.common.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.driver.item.MutableProcessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.crafting.CraftingInput;

/** The data transforms formerly performed by ExtendedRecipe.addNBTToResult. */
record RecipeOperation(String operation, int amount, Either<Integer, String> color, String disk) {
    private static final Set<String> OPERATIONS = Set.of("default_architecture", "navigation_map", "linked_cards",
            "lua_bios", "loot_disk", "copy_eeprom", "swap_eeprom", "format_disk", "format_eeprom",
            "color_floppy", "color_chamelium", "print_beacon", "print_light");
    static final MapCodec<RecipeOperation> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("operation").forGetter(RecipeOperation::operation),
            Codec.INT.optionalFieldOf("amount", 1).forGetter(RecipeOperation::amount),
            Codec.either(Codec.INT, Codec.STRING).optionalFieldOf("color", Either.right("")).forGetter(RecipeOperation::color),
            Codec.STRING.optionalFieldOf("disk", "").forGetter(RecipeOperation::disk)
    ).apply(instance, RecipeOperation::new));

    RecipeOperation {
        if (!OPERATIONS.contains(operation)) throw new IllegalArgumentException("Unknown OpenComputers crafting operation: " + operation);
        if (operation.equals("color_floppy") && (color.left().orElse(-1) < 0 || color.left().orElse(16) > 15))
            throw new IllegalArgumentException("Floppy dye index must be in the original 0..15 range");
        if (operation.equals("loot_disk") && disk.isEmpty()) throw new IllegalArgumentException("A loot disk recipe must name its filesystem factory");
        if (operation.equals("print_light") && amount <= 0) throw new IllegalArgumentException("Print light increment must be positive");
    }

    boolean canApply(CraftingInput input) {
        if (operation.equals("print_beacon") || operation.equals("print_light")) {
            ItemStack print = find(input, "print");
            if (print.isEmpty()) return false;
            CompoundTag data = Recipes.customData(print);
            return operation.equals("print_beacon") ? !data.getBoolean("isBeaconBase")
                    : Math.min(15, data.getByte("lightLevel") & 0xff) < 15;
        }
        return true;
    }

    ItemStack displayResult(ItemStack template) {
        ItemStack result = template.copy();
        switch (operation) {
            case "lua_bios" -> {
                result = Recipes.definedItem("luabios");
                if (Recipes.customData(result).getCompound("oc:data").getByteArray("oc:eeprom").length == 0)
                    throw new IllegalStateException("The Lua BIOS item is missing its EEPROM program");
            }
            case "loot_disk" -> result = Recipes.lootDisk(disk);
            case "default_architecture" -> {
                DriverItem driver = Driver.driverFor(result);
                if (!(driver instanceof MutableProcessor processor))
                    throw new IllegalStateException("Processor driver has not been initialized for " + result);
                processor.setArchitecture(result, Recipes.defaultArchitecture());
            }
            case "color_floppy" -> {
                CompoundTag root = Recipes.customData(result);
                root.putInt("oc:color", color.left().orElseThrow());
                Recipes.customData(result, root);
            }
            case "color_chamelium" -> {
                String value = color.right().orElseThrow();
                Map<String, String> properties = new LinkedHashMap<>(result.getOrDefault(DataComponents.BLOCK_STATE,
                        BlockItemStateProperties.EMPTY).properties());
                properties.put("color", value);
                result.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.copyOf(properties)));
            }
            case "print_beacon", "print_light" -> {
                CompoundTag root = Recipes.customData(result);
                if (operation.equals("print_beacon")) root.putBoolean("isBeaconBase", true);
                else root.putByte("lightLevel", (byte) Math.min(15, amount));
                Recipes.customData(result, root);
            }
            default -> {}
        }
        return result;
    }

    ItemStack assemble(ItemStack template, CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = displayResult(template);
        switch (operation) {
            case "navigation_map" -> {
                ItemStack map = input.items().stream().filter(stack -> stack.is(Items.FILLED_MAP)).findFirst().orElse(ItemStack.EMPTY);
                if (map.isEmpty()) return ItemStack.EMPTY;
                DriverItem driver = requireDriver(result);
                CompoundTag data = environmentData(driver, result);
                data.put("oc:map", map.copyWithCount(1).saveOptional(registries));
                driver.saveDataTag(result, data);
            }
            case "linked_cards" -> {
                DriverItem driver = requireDriver(result);
                CompoundTag data = environmentData(driver, result);
                data.putString("oc:tunnel", UUID.randomUUID().toString());
                driver.saveDataTag(result, data);
            }
            case "copy_eeprom" -> {
                for (ItemStack source : input.items()) {
                    if (Recipes.is(source, "eeprom") && source.has(DataComponents.CUSTOM_DATA)) {
                        result = source.copyWithCount(template.getCount());
                        CompoundTag root = Recipes.customData(result);
                        CompoundTag data = root.getCompound("oc:data");
                        if (data.contains("node", Tag.TAG_COMPOUND)) {
                            CompoundTag node = data.getCompound("node");
                            node.remove("address");
                            data.put("node", node);
                            root.put("oc:data", data);
                        }
                        Recipes.customData(result, root);
                        break;
                    }
                }
            }
            case "swap_eeprom" -> result = swapEeprom(template, input, registries);
            case "format_disk" -> {
                CompoundTag root = new CompoundTag();
                for (ItemStack source : input.items()) {
                    CompoundTag old = Recipes.customData(source);
                    if (Recipes.is(source, "floppy") && old.contains("oc:color", Tag.TAG_ANY_NUMERIC))
                        root.put("oc:color", old.get("oc:color").copy());
                }
                Recipes.customData(result, root);
            }
            case "format_eeprom" -> Recipes.customData(result, new CompoundTag());
            case "print_beacon", "print_light" -> {
                result = find(input, "print").copyWithCount(1);
                if (result.isEmpty()) return ItemStack.EMPTY;
                CompoundTag root = Recipes.customData(result);
                if (operation.equals("print_beacon")) {
                    if (root.getBoolean("isBeaconBase")) return ItemStack.EMPTY;
                    root.putBoolean("isBeaconBase", true);
                } else {
                    int old = Math.min(15, root.getByte("lightLevel") & 0xff);
                    if (old >= 15) return ItemStack.EMPTY;
                    root.putByte("lightLevel", (byte) Math.min(15, old + amount));
                }
                Recipes.customData(result, root);
            }
            default -> {}
        }
        return result;
    }

    private static DriverItem requireDriver(ItemStack stack) {
        DriverItem driver = Driver.driverFor(stack);
        if (driver == null) throw new IllegalStateException("OpenComputers component driver is not initialized for " + stack);
        return driver;
    }

    private static CompoundTag environmentData(DriverItem driver, ItemStack stack) {
        CompoundTag data = driver.dataTag(stack);
        return data == null ? Recipes.customData(stack).getCompound("oc:data") : data.copy();
    }

    private static ItemStack find(CraftingInput input, String name) {
        return input.items().stream().filter(stack -> Recipes.is(stack, name)).findFirst().orElse(ItemStack.EMPTY);
    }

    private static ItemStack swapEeprom(ItemStack template, CraftingInput input, HolderLookup.Provider registries) {
        ItemStack device = input.items().stream().filter(stack -> !stack.isEmpty() && stack.is(template.getItem()))
                .findFirst().orElse(ItemStack.EMPTY);
        ItemStack eeprom = find(input, "eeprom");
        if (device.isEmpty() || eeprom.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = device.copyWithCount(template.getCount());
        CompoundTag root = Recipes.customData(result);
        if (Recipes.is(device, "tablet")) {
            ListTag oldItems = root.getList("oc:items", Tag.TAG_COMPOUND);
            ListTag items = new ListTag();
            boolean[] occupied = new boolean[32];
            int replacementSlot = -1;
            for (Tag tag : oldItems) {
                CompoundTag slot = (CompoundTag) tag;
                int index = slot.getByte("slot");
                ItemStack old = ItemStack.parseOptional(registries, slot.getCompound("item"));
                if (Recipes.is(old, "eeprom")) {
                    if (replacementSlot < 0) replacementSlot = index;
                } else {
                    items.add(slot.copy());
                    if (index >= 0 && index < occupied.length) occupied[index] = true;
                }
            }
            if (replacementSlot < 0 || replacementSlot >= occupied.length || occupied[replacementSlot]) {
                replacementSlot = 0;
                while (replacementSlot < occupied.length && occupied[replacementSlot]) replacementSlot++;
            }
            if (replacementSlot >= occupied.length) return ItemStack.EMPTY;
            CompoundTag replacement = new CompoundTag();
            replacement.putByte("slot", (byte) replacementSlot);
            replacement.put("item", eeprom.copyWithCount(1).saveOptional(registries));
            items.add(replacement);
            root.put("oc:items", items);
        } else {
            ListTag components = new ListTag();
            for (Tag tag : root.getList("oc:components", Tag.TAG_COMPOUND)) {
                CompoundTag serialized = (CompoundTag) tag;
                ItemStack old = ItemStack.parseOptional(registries, serialized);
                if (!Recipes.is(old, "eeprom")) components.add(serialized.copy());
            }
            components.add(eeprom.copyWithCount(1).saveOptional(registries));
            root.put("oc:components", components);
        }
        Recipes.customData(result, root);
        return result;
    }

    void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(operation);
        buffer.writeVarInt(amount);
        buffer.writeBoolean(color.left().isPresent());
        if (color.left().isPresent()) buffer.writeVarInt(color.left().orElseThrow());
        else buffer.writeUtf(color.right().orElseThrow());
        buffer.writeUtf(disk);
    }

    static RecipeOperation read(RegistryFriendlyByteBuf buffer) {
        String operation = buffer.readUtf();
        int amount = buffer.readVarInt();
        Either<Integer, String> color = buffer.readBoolean() ? Either.left(buffer.readVarInt()) : Either.right(buffer.readUtf());
        return new RecipeOperation(operation, amount, color, buffer.readUtf());
    }
}
