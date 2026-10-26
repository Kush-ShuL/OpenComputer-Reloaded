package li.cil.oc.common.init;

import li.cil.oc.Settings;
import li.cil.oc.common.block.HologramBlock;
import li.cil.oc.common.block.entity.HologramBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Holograms {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "opencomputers");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "opencomputers");
    public static final List<Supplier<HologramBlock>> HOLOGRAMS = new ArrayList<>();
    public static final List<Supplier<BlockEntityType<HologramBlockEntity>>> HOLOGRAM_TYPES = new ArrayList<>();
    private Holograms() {}
    static {
        for (int index = 0; index < 2; index++) {
            int tier = index; String id = "hologram" + (tier + 1);
            var block = BLOCKS.register(id, () -> new HologramBlock(tier, BlockBehaviour.Properties.of().strength(2, 5).sound(SoundType.METAL).noOcclusion().lightLevel(state -> Settings.get().hologramLight ? 15 : 0), () -> HOLOGRAM_TYPES.get(tier).get()));
            HOLOGRAMS.add(block);
            HOLOGRAM_TYPES.add(TYPES.register(id, () -> BlockEntityType.Builder.of((position, state) -> new HologramBlockEntity(HOLOGRAM_TYPES.get(tier).get(), position, state, tier), block.get()).build(null)));
            var item = ITEMS.register(id, () -> new HologramItem(block.get(), tier));
            Items.INSTANCE.registerBlock(id, block, item);
        }
    }
    public static void register(IEventBus modBus) { BLOCKS.register(modBus); ITEMS.register(modBus); TYPES.register(modBus); }
    private static final class HologramItem extends BlockItem {
        private final int tier;
        private HologramItem(Block block, int tier) { super(block, new Item.Properties().rarity(tier == 0 ? Rarity.COMMON : Rarity.UNCOMMON)); this.tier = tier; }
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) { super.appendHoverText(stack, context, tooltip, flag); tooltip.add(Component.translatable("oc:tooltip.hologram" + tier)); }
    }
}
