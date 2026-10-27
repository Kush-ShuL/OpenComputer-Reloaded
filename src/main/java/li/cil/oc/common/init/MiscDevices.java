package li.cil.oc.common.init;

import java.util.function.Supplier;
import li.cil.oc.common.item.HoverBootsItem;
import li.cil.oc.common.item.PresentItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The original remaining ordinary block, seasonal item and wearable hardware. */
public final class MiscDevices {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "opencomputers");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "opencomputers");
    public static final Supplier<Block> ENDSTONE = BLOCKS.register("endstone", () -> new Block(BlockBehaviour.Properties.of().strength(3, net.minecraft.world.level.block.Blocks.END_STONE.getExplosionResistance()).sound(SoundType.STONE).isValidSpawn((state, level, pos, type) -> false)));
    public static final Supplier<BlockItem> ENDSTONE_ITEM = ITEMS.register("endstone", () -> new BlockItem(ENDSTONE.get(), new Item.Properties()));
    public static final Supplier<HoverBootsItem> HOVER_BOOTS = ITEMS.register("hoverboots", () -> new HoverBootsItem(new Item.Properties()));
    public static final Supplier<PresentItem> PRESENT = ITEMS.register("present", () -> new PresentItem(new Item.Properties()));
    static {
        Items.INSTANCE.registerBlock("endstone", ENDSTONE, ENDSTONE_ITEM);
        Items.INSTANCE.registerItem("hoverboots", HOVER_BOOTS);
        Items.INSTANCE.registerItem("present", PRESENT);
    }
    private MiscDevices() {}
    public static void initializeDrivers() {
        li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem) new li.cil.oc.server.driver.item.ContainerFloppy());
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus);
        bus.addListener(MiscDevices::capabilities);
        NeoForge.EVENT_BUS.register(li.cil.oc.common.MiscEvents.class);
        NeoForge.EVENT_BUS.register(li.cil.oc.common.event.HoverBootsHandler.class);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) li.cil.oc.client.MiscClient.register(bus);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM,
                (stack, context) -> li.cil.oc.common.capabilities.ExternalPower.expose(() -> HOVER_BOOTS.get().energyStorage(stack)),
                HOVER_BOOTS.get());
    }
}
