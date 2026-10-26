package li.cil.oc.common.integration;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Replace only the selected weighted placeholder with the complete original program disk. */
public final class ProgramDiskLootModifier extends LootModifier {
    public static final MapCodec<ProgramDiskLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, ProgramDiskLootModifier::new));
    private ProgramDiskLootModifier(LootItemCondition[] conditions) { super(conditions); }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generated, LootContext context) {
        for (int index = generated.size() - 1; index >= 0; index--) {
            ItemStack stack = generated.get(index);
            if (!stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(LootIntegration.PENDING)) continue;
            generated.remove(index);
            for (int count = 0; count < stack.getCount(); count++) {
                ItemStack disk = LootIntegration.randomDisk(context.getLevel(), context.getRandom());
                if (!disk.isEmpty()) generated.add(index, disk);
            }
        }
        return generated;
    }
    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
