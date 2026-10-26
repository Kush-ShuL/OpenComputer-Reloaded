package ocfixture;

import java.util.List;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.machine.Context;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;

/** Real chest generation, including NeoForge's global modifiers, and mounted program bytes. */
final class LootContract {
    private LootContract() {}
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    static ItemStack generate(ServerLevel level, List<String> results) {
        ItemStack floppy = li.cil.oc.api.Items.get("floppy").createItemStack(1), sample = ItemStack.EMPTY;
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        for (String target : List.of("simple_dungeon", "desert_pyramid", "jungle_temple", "stronghold_library")) {
            var key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/" + target));
            var table = level.getServer().reloadableRegistries().getLootTable(key);
            int disks = 0, vanilla = 0;
            for (int roll = 1; roll <= 256; roll++) {
                for (ItemStack stack : table.getRandomItems(params, 0x4f430000L + roll)) {
                    CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    require(!data.getBoolean("oc:worldLoot"), "Weighted loot marker escaped the real global modifier: " + target);
                    if (stack.is(floppy.getItem())) {
                        String factory = data.getString("oc:lootFactory");
                        require(!factory.isBlank(), "Chest produced an unconfigured blank program disk: " + target);
                        require(!List.of("openos", "oppm", "data", "generator").contains(factory), "A disk with zero original loot weight spawned: " + factory);
                        disks += stack.getCount();
                        if (sample.isEmpty()) sample = stack.copyWithCount(1);
                    } else vanilla += stack.getCount();
                }
            }
            require(disks > 0 && vanilla > 0, "Chest generation lost program disks or its original vanilla loot: " + target);
            results.add("PASS loot." + target + " actual seeded generation: program_disks=" + disks + " vanilla_items=" + vanilla + " (256 rolls; not a probability-distribution assertion)");
        }
        return sample;
    }

    static void readProgram(Component filesystem, Context context, List<String> results) throws Exception {
        require(Boolean.TRUE.equals(filesystem.invoke("isReadOnly", context)[0]), "Loot program mounted as a writable blank disk");
        String path = file(filesystem, context, "", 0);
        require(path != null, "Real generated program disk has no readable files");
        Object handle = filesystem.invoke("open", context, path, "r")[0];
        try {
            Object bytes = filesystem.invoke("read", context, handle, 64)[0];
            require(bytes instanceof byte[] value && value.length > 0, "Generated program filesystem did not return actual bundled bytes");
        } finally { filesystem.invoke("close", context, handle); }
        results.add("PASS loot.mount real generated disk mounted in rack and read bundled program bytes from " + path);
    }

    private static String file(Component filesystem, Context context, String directory, int depth) throws Exception {
        if (depth > 8) return null;
        Object[] values = filesystem.invoke("list", context, directory);
        if (values == null || values.length == 0 || !(values[0] instanceof String[] entries)) return null;
        for (String name : entries) {
            String path = directory + name;
            if (Boolean.TRUE.equals(filesystem.invoke("isDirectory", context, path)[0])) {
                String found = file(filesystem, context, path.endsWith("/") ? path : path + "/", depth + 1);
                if (found != null) return found;
            } else if (((Number) filesystem.invoke("size", context, path)[0]).longValue() > 0) return path;
        }
        return null;
    }
}
