package li.cil.oc.common.init;

import java.util.function.Supplier;
import li.cil.oc.common.block.AdapterBlock;
import li.cil.oc.common.block.entity.AdapterBlockEntity;
import li.cil.oc.common.item.UpgradeMFItem;
import li.cil.oc.common.menu.AdapterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The adapter, its original MFU upgrade, and built-in block integration drivers. */
public final class Adapters {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "opencomputers");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "opencomputers");
    public static final Supplier<AdapterBlock> BLOCK = BLOCKS.register("adapter", () -> new AdapterBlock(BlockBehaviour.Properties.of().strength(2, 5).sound(SoundType.METAL), () -> Adapters.TYPE.get()));
    public static final Supplier<BlockEntityType<AdapterBlockEntity>> TYPE = TYPES.register("adapter", () -> BlockEntityType.Builder.of((pos, state) -> new AdapterBlockEntity(Adapters.TYPE.get(), pos, state), BLOCK.get()).build(null));
    public static final Supplier<BlockItem> ITEM = ITEMS.register("adapter", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final Supplier<UpgradeMFItem> MFU = ITEMS.register("mfu", () -> new UpgradeMFItem(new Item.Properties().rarity(Rarity.RARE)));
    private static boolean driversInitialized;
    static { Items.INSTANCE.registerBlock("adapter", BLOCK, ITEM); Items.INSTANCE.registerItem("mfu", MFU); }
    private Adapters() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TYPES.register(bus); AdapterMenu.register(bus); bus.addListener(Adapters::capabilities); }
    private static void capabilities(RegisterCapabilitiesEvent event) { event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TYPE.get(), (adapter, side) -> new InvWrapper(adapter)); }
    public static synchronized void initializeDrivers() {
        if (driversInitialized) return;
        var mfu = new li.cil.oc.server.driver.item.UpgradeMF();
        li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem) mfu); li.cil.oc.api.Driver.add((li.cil.oc.api.driver.EnvironmentProvider) mfu);
        li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.block.Inventory());
        li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.block.Furnace());
        li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.block.FluidHandler());
        li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.block.EnergyStorage());
        driversInitialized = true;
    }
}
