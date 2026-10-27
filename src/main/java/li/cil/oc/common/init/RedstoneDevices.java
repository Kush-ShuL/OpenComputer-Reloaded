package li.cil.oc.common.init;

import java.util.function.Supplier;
import li.cil.oc.common.block.RedstoneBlock;
import li.cil.oc.common.block.entity.RedstoneBlockEntity;
import li.cil.oc.common.item.RedstoneCardItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Original vanilla/tier-two bundled cards and external sixteen-channel redstone I/O. */
public final class RedstoneDevices {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "opencomputers");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "opencomputers");
    public static final Supplier<RedstoneBlock> BLOCK = BLOCKS.register("redstone", () -> new RedstoneBlock(BlockBehaviour.Properties.of().strength(2, 5).sound(SoundType.METAL).isValidSpawn((state,level,pos,type)->false), () -> RedstoneDevices.TYPE.get()));
    public static final Supplier<BlockEntityType<RedstoneBlockEntity>> TYPE = TYPES.register("redstone", () -> BlockEntityType.Builder.of((pos, state) -> new RedstoneBlockEntity(RedstoneDevices.TYPE.get(), pos, state), BLOCK.get()).build(null));
    public static final Supplier<BlockItem> ITEM = ITEMS.register("redstone", () -> new li.cil.oc.common.item.MachineHostBlockItem(BLOCK.get(), new Item.Properties()));
    public static final Supplier<RedstoneCardItem> CARD = ITEMS.register("redstonecard1", () -> new RedstoneCardItem(new Item.Properties(), 0));
    public static final Supplier<RedstoneCardItem> BUNDLED_CARD = ITEMS.register("redstonecard2", () -> new RedstoneCardItem(new Item.Properties(), 1));
    static { Items.INSTANCE.registerBlock("redstone", BLOCK, ITEM); Items.INSTANCE.registerItem("redstonecard1", CARD); Items.INSTANCE.registerItem("redstonecard2", BUNDLED_CARD); }
    private RedstoneDevices() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TYPES.register(bus); }
    public static void initializeDrivers() {
        var card = new li.cil.oc.server.driver.item.RedstoneCard();
        li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem) card);
        li.cil.oc.api.Driver.add((li.cil.oc.api.driver.EnvironmentProvider) card);
    }
}
