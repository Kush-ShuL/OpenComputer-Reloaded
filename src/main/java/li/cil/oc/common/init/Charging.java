package li.cil.oc.common.init;

import java.util.function.Supplier;
import li.cil.oc.common.block.ChargerBlock;
import li.cil.oc.common.block.entity.ChargerBlockEntity;
import li.cil.oc.common.integration.ItemCharge;
import li.cil.oc.common.menu.ChargerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Charging {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "opencomputers");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "opencomputers");
    public static final Supplier<ChargerBlock> BLOCK = BLOCKS.register("charger", () -> new ChargerBlock(BlockBehaviour.Properties.of().strength(2, 5).sound(SoundType.METAL), () -> Charging.TYPE.get()));
    public static final Supplier<BlockEntityType<ChargerBlockEntity>> TYPE = TYPES.register("charger", () -> BlockEntityType.Builder.of((pos, state) -> new ChargerBlockEntity(Charging.TYPE.get(), pos, state), BLOCK.get()).build(null));
    public static final Supplier<BlockItem> ITEM = ITEMS.register("charger", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    static { Items.INSTANCE.registerBlock("charger", BLOCK, ITEM); }
    private Charging() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); TYPES.register(bus); ChargerMenu.register(bus);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TYPE.get(), (charger, side) -> new SidedInvWrapper(charger, side)));
        ItemCharge.add(new ItemCharge.Handler("OpenComputers Chargeable items", stack -> stack.getItem() instanceof li.cil.oc.api.driver.item.Chargeable item && item.canCharge(stack),
                (stack, amount, simulate) -> ((li.cil.oc.api.driver.item.Chargeable) stack.getItem()).charge(stack, amount, simulate)));
        if (FMLEnvironment.dist == Dist.CLIENT) li.cil.oc.client.ChargingClient.register(bus);
    }
}
