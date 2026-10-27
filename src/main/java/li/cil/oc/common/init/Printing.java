package li.cil.oc.common.init;

import java.util.function.Supplier;
import li.cil.oc.Settings;
import li.cil.oc.common.block.PrintBlock;
import li.cil.oc.common.block.PrinterBlock;
import li.cil.oc.common.block.entity.PrintBlockEntity;
import li.cil.oc.common.block.entity.PrinterBlockEntity;
import li.cil.oc.common.item.PrintBlockItem;
import li.cil.oc.common.item.data.PrintData;
import li.cil.oc.common.menu.PrinterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Original printing catalog, usable inventory capabilities and built-in ink providers. */
public final class Printing {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "opencomputers");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "opencomputers");
    public static final Supplier<PrinterBlock> PRINTER = BLOCKS.register("printer", () -> new PrinterBlock(BlockBehaviour.Properties.of().strength(2, 5).sound(SoundType.METAL).noOcclusion(), () -> Printing.PRINTER_TYPE.get()));
    public static final Supplier<BlockEntityType<PrinterBlockEntity>> PRINTER_TYPE = TYPES.register("printer", () -> BlockEntityType.Builder.of((pos, state) -> new PrinterBlockEntity(Printing.PRINTER_TYPE.get(), pos, state), PRINTER.get()).build(null));
    public static final Supplier<BlockItem> PRINTER_ITEM = ITEMS.register("printer", () -> new li.cil.oc.common.item.MachineHostBlockItem(PRINTER.get(), new Item.Properties()));
    public static final Supplier<PrintBlock> PRINT = BLOCKS.register("print", () -> new PrintBlock(BlockBehaviour.Properties.of().strength(1).sound(SoundType.STONE).noOcclusion().dynamicShape(), () -> Printing.PRINT_TYPE.get()));
    public static final Supplier<BlockEntityType<PrintBlockEntity>> PRINT_TYPE = TYPES.register("print", () -> BlockEntityType.Builder.of((pos, state) -> new PrintBlockEntity(Printing.PRINT_TYPE.get(), pos, state), PRINT.get()).build(null));
    public static final Supplier<PrintBlockItem> PRINT_ITEM = ITEMS.register("print", () -> new PrintBlockItem(PRINT.get(), new Item.Properties()));
    static { Items.INSTANCE.registerBlock("printer", PRINTER, PRINTER_ITEM); Items.INSTANCE.registerBlock("print", PRINT, PRINT_ITEM); }
    private Printing() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); TYPES.register(bus); PrinterMenu.register(bus); bus.addListener(Printing::capabilities);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> event.remove(new ItemStack(PRINT_ITEM.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        try { PrintData.addInkProvider(Printing.class.getMethod("inkCartridgeInkProvider", ItemStack.class)); PrintData.addInkProvider(Printing.class.getMethod("dyeInkProvider", ItemStack.class)); }
        catch (ReflectiveOperationException impossible) { throw new ExceptionInInitializerError(impossible); }
        if (FMLEnvironment.dist == Dist.CLIENT) li.cil.oc.client.PrintingClient.register(bus);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) { event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PRINTER_TYPE.get(), (printer, side) -> new SidedInvWrapper(printer, side)); }
    public static int inkCartridgeInkProvider(ItemStack stack) { return !stack.isEmpty() && li.cil.oc.api.Items.get(stack) == li.cil.oc.api.Items.get("inkcartridge") ? Settings.get().printInkValue : 0; }
    public static int dyeInkProvider(ItemStack stack) { return !stack.isEmpty() && (stack.getItem() instanceof DyeItem || stack.is(net.neoforged.neoforge.common.Tags.Items.DYES)) ? Settings.get().printInkValue / 10 : 0; }
}
