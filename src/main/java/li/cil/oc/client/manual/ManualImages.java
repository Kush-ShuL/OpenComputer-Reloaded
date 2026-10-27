package li.cil.oc.client.manual;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import li.cil.oc.api.Items;
import li.cil.oc.api.manual.ImageRenderer;
import li.cil.oc.api.manual.InteractiveImageRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.BlockItemStateProperties;

/** The original texture/item/block/ore-dictionary manual renderers, using modern registries and tags. */
final class ManualImages {
    private ManualImages() {}
    private static final Map<String, String> LEGACY_NAMES = Map.ofEntries(
        Map.entry("circuitchip1", "chip1"), Map.entry("circuitchip2", "chip2"), Map.entry("circuitchip3", "chip3"),
        Map.entry("materialalu", "alu"), Map.entry("materialcu", "cu"), Map.entry("materialarrowkey", "arrowkeys"),
        Map.entry("materialbuttongroup", "buttongroup"), Map.entry("materialcard", "card"),
        Map.entry("materialcircuitboard", "circuitboard"), Map.entry("materialcircuitboardprinted", "printedcircuitboard"),
        Map.entry("materialcircuitboardraw", "rawcircuitboard"), Map.entry("materialcuttingwire", "cuttingwire"),
        Map.entry("materialdisk", "disk"), Map.entry("materialinterweb", "interweb"), Map.entry("materialnumpad", "numpad"),
        Map.entry("materialtransistor", "transistor"), Map.entry("materialacid", "acid"),
        Map.entry("serverrack", "rack"), Map.entry("switch", "relay"), Map.entry("accesspoint", "relay")
    );
    // Original PIN common/init/Items.scala registers each Delegator's subitems in this order.
    private static final Map<String, List<String>> LEGACY_ITEMS = Map.of(
        "material", List.of("cuttingwire", "acid", "rawcircuitboard", "circuitboard", "printedcircuitboard", "card", "transistor",
                "chip1", "chip2", "chip3", "alu", "cu", "disk", "interweb", "buttongroup", "arrowkeys", "numpad",
                "tabletcase1", "tabletcase2", "tabletcasecreative", "microcontrollercase1", "microcontrollercase2", "microcontrollercasecreative",
                "dronecase1", "dronecase2", "dronecasecreative", "inkcartridgeempty", "inkcartridge", "chamelium", "chipdiamond"),
        "tool", List.of("analyzer", "debugger", "terminal", "texturepicker", "manual", "nanomachines"),
        "component", List.of("cpu1", "cpu2", "cpu3", "componentbus1", "componentbus2", "componentbus3",
                "ram1", "ram2", "ram3", "ram4", "ram5", "ram6", "servercreative", "server1", "server2", "server3",
                "apu1", "apu2", "apucreative", "terminalserver", "diskdrivemountable"),
        "card", List.of("debugcard", "graphicscard1", "graphicscard2", "graphicscard3", "redstonecard1", "redstonecard2",
                "lancard", "wlancard2", "internetcard", "linkedcard", "datacard1", "datacard2", "datacard3"),
        "upgrade", List.of("angelupgrade", "batteryupgrade1", "batteryupgrade2", "batteryupgrade3", "chunkloaderupgrade",
                "cardcontainer1", "cardcontainer2", "cardcontainer3", "upgradecontainer1", "upgradecontainer2", "upgradecontainer3",
                "craftingupgrade", "databaseupgrade1", "databaseupgrade2", "databaseupgrade3", "experienceupgrade", "generatorupgrade",
                "inventoryupgrade", "inventorycontrollerupgrade", "navigationupgrade", "pistonupgrade", "signupgrade", "solargeneratorupgrade",
                "tankupgrade", "tankcontrollerupgrade", "tractorbeamupgrade", "leashupgrade", "hoverupgrade1", "hoverupgrade2",
                "tradingupgrade", "mfu", "wlancard1", "componentbuscreative", "stickypistonupgrade"),
        "storage", List.of("eeprom", "floppy", "hdd1", "hdd2", "hdd3"),
        "misc", List.of("tablet", "drone", "present")
    );

    static void register(ManualRegistry manual) {
        manual.addProvider("", ManualImages::texture);
        manual.addProvider("item", data -> registryItem(data, false));
        manual.addProvider("block", data -> registryItem(data, true));
        manual.addProvider("oredict", ManualImages::oreDictionary);
        manual.addProvider("tag", ManualImages::tag);
    }

    private static ImageRenderer texture(String data) {
        ResourceLocation location = ResourceLocation.tryParse(data.toLowerCase(Locale.ROOT).replace(":/", ":"));
        if (location == null) return missing("Image");
        var resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) return missing("Image");
        try (var stream = resource.get().open(); var image = NativeImage.read(stream)) {
            return new Texture(location, image.getWidth(), image.getHeight());
        } catch (Exception error) { return missing("Image"); }
    }

    private static ImageRenderer registryItem(String data, boolean block) {
        // Flattened registries have no damage-based sub-items. Named OC descriptors retain their original identities.
        int at = data.lastIndexOf('@');
        String name = at < 0 ? data : data.substring(0, at);
        int metadata = 0;
        if (at >= 0) {
            try { metadata = Integer.parseInt(data.substring(at + 1)); }
            catch (NumberFormatException error) { return missing(block ? "Block" : "Item"); }
        }
        ResourceLocation id = ResourceLocation.tryParse(name.toLowerCase(Locale.ROOT));
        if (id == null) return missing(block ? "Block" : "Item");
        if (id.getNamespace().equals("opencomputers")) {
            if (!block && id.getPath().equals("item") && at >= 0) {
                // These old shared-item references remain in the original tablet/debug-card/drone pages.
                String legacy = switch (metadata) { case 68 -> "tablet"; case 73 -> "debugcard"; case 84 -> "drone"; default -> null; };
                var definition = legacy == null ? null : Items.get(legacy);
                if (definition != null) return stack(definition.createItemStack(1));
            }
            if (!block && LEGACY_ITEMS.containsKey(id.getPath()) && (at >= 0 || Items.get(id.getPath()) == null)) {
                List<String> names = LEGACY_ITEMS.get(id.getPath());
                var definition = metadata >= 0 && metadata < names.size() ? Items.get(names.get(metadata)) : null;
                return definition == null ? missing("Item") : stack(definition.createItemStack(1));
            }
            var definition = Items.get(id.getPath());
            if (definition != null && (!block || definition.block() != null)) {
                ItemStack value = definition.createItemStack(1);
                if (id.getPath().equals("chameliumblock") && metadata >= 0 && metadata < 16)
                    value.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("color", DyeColor.byId(15 - metadata).getName())));
                else if (metadata != 0) return missing(block ? "Block" : "Item");
                return stack(value);
            }
        }
        if (metadata != 0) return missing(block ? "Block" : "Item");
        if (block) {
            if (!BuiltInRegistries.BLOCK.containsKey(id)) return missing("Block");
            return stack(new ItemStack(BuiltInRegistries.BLOCK.get(id)));
        }
        if (!BuiltInRegistries.ITEM.containsKey(id)) return missing("Item");
        return stack(new ItemStack(BuiltInRegistries.ITEM.get(id)));
    }

    private static ImageRenderer oreDictionary(String data) {
        if (data.startsWith("oc:")) {
            String name = data.substring(3).toLowerCase(Locale.ROOT);
            String mapped = LEGACY_NAMES.getOrDefault(name, name);
            var candidates = stacksInTag("opencomputers:" + mapped);
            if (!candidates.isEmpty()) return new Stacks(candidates);
            var definition = Items.get(mapped);
            return definition == null ? missing("OreDict") : stack(definition.createItemStack(1));
        }
        if (data.equals("craftingPiston")) return stack(new ItemStack(net.minecraft.world.item.Items.PISTON));
        var tags = new ArrayList<String>();
        tags.add(data);
        String[][] legacy = {{"ingot", "ingots"}, {"nugget", "nuggets"}, {"dust", "dusts"}, {"gem", "gems"}, {"ore", "ores"}, {"block", "storage_blocks"}};
        for (String[] prefix : legacy) {
            if (data.startsWith(prefix[0]) && data.length() > prefix[0].length()) {
                String material = data.substring(prefix[0].length()).replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
                tags.add("c:" + prefix[1] + "/" + material);
            }
        }
        for (String candidate : tags) {
            var result = stacksInTag(candidate);
            if (!result.isEmpty()) return new Stacks(result);
        }
        return missing("OreDict");
    }

    private static ImageRenderer tag(String data) {
        var result = stacksInTag(data);
        return result.isEmpty() ? missing("OreDict") : new Stacks(result);
    }

    private static List<ItemStack> stacksInTag(String data) {
        var result = new ArrayList<ItemStack>();
        ResourceLocation id = ResourceLocation.tryParse(data.toLowerCase(Locale.ROOT));
        if (id != null) BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(holders ->
            holders.forEach(holder -> { var value = new ItemStack(holder.value()); if (!value.isEmpty()) result.add(value); }));
        return result;
    }

    private static ImageRenderer stack(ItemStack stack) {
        return stack.isEmpty() ? missing("Item") : new Stacks(List.of(stack.copy()));
    }

    private static ImageRenderer missing(String kind) {
        return new Missing("oc:gui.Manual.Warning." + kind + "Missing");
    }

    private record Texture(ResourceLocation location, int width, int height) implements ImageRenderer {
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY) {
            graphics.blit(location, 0, 0, 0, 0, width, height, width, height);
        }
    }

    private record Missing(String tooltip) implements InteractiveImageRenderer {
        @Override public int getWidth() { return 16; }
        @Override public int getHeight() { return 16; }
        @Override public String getTooltip(String original) { return tooltip; }
        @Override public boolean onMouseClick(int mouseX, int mouseY) { return false; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY) {
            graphics.blit(ManualRegistry.texture("manual_missing_item"), 0, 0, 0, 0, 16, 16, 16, 16);
        }
    }

    private record Stacks(List<ItemStack> values) implements ImageRenderer {
        @Override public int getWidth() { return 32; }
        @Override public int getHeight() { return 32; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY) {
            int index = (int) ((System.currentTimeMillis() / 1000) % values.size());
            graphics.pose().pushPose();
            try { graphics.pose().scale(2, 2, 1); graphics.renderItem(values.get(index), 0, 0); }
            finally { graphics.pose().popPose(); }
        }
    }
}
