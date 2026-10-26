package li.cil.oc.common.init;

import java.util.function.Supplier;
import li.cil.oc.common.block.DiskDriveBlock;
import li.cil.oc.common.block.entity.DiskDriveBlockEntity;
import li.cil.oc.common.menu.DiskDriveMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registrations for the original external, networked floppy disk drive. */
public final class DiskDrives {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "opencomputers");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "opencomputers");
    public static final Supplier<DiskDriveBlock> BLOCK = BLOCKS.register("diskdrive", () -> new DiskDriveBlock(
        BlockBehaviour.Properties.of().strength(2, 5).sound(SoundType.METAL), () -> DiskDrives.TYPE.get()));
    public static final Supplier<BlockEntityType<DiskDriveBlockEntity>> TYPE = TYPES.register("diskdrive", () ->
        BlockEntityType.Builder.of((pos, state) -> new DiskDriveBlockEntity(DiskDrives.TYPE.get(), pos, state), BLOCK.get()).build(null));
    public static final Supplier<BlockItem> ITEM = ITEMS.register("diskdrive", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    static { Items.INSTANCE.registerBlock("diskdrive", BLOCK, ITEM); }
    private DiskDrives() {}

    /** The only common bootstrap call required by this module. */
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        DiskDriveMenu.register(bus);
        bus.addListener(DiskDrives::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TYPE.get(), (drive, side) -> new InvWrapper(drive));
    }
}
