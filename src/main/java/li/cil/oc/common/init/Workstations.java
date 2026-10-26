package li.cil.oc.common.init;

import java.util.function.Supplier;
import li.cil.oc.common.block.WorkstationBlock;
import li.cil.oc.common.block.entity.*;
import li.cil.oc.common.menu.WorkstationMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Workstations {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "opencomputers");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "opencomputers");
    public static final Supplier<WorkstationBlock> ASSEMBLER = BLOCKS.register("assembler", () -> new WorkstationBlock(metal().noOcclusion(), true, () -> Workstations.ASSEMBLER_TYPE.get()));
    public static final Supplier<BlockEntityType<AssemblerBlockEntity>> ASSEMBLER_TYPE = TYPES.register("assembler", () -> BlockEntityType.Builder.of((pos, state) -> new AssemblerBlockEntity(Workstations.ASSEMBLER_TYPE.get(), pos, state), ASSEMBLER.get()).build(null));
    public static final Supplier<WorkstationBlock> DISASSEMBLER = BLOCKS.register("disassembler", () -> new WorkstationBlock(metal(), false, () -> Workstations.DISASSEMBLER_TYPE.get()));
    public static final Supplier<BlockEntityType<DisassemblerBlockEntity>> DISASSEMBLER_TYPE = TYPES.register("disassembler", () -> BlockEntityType.Builder.of((pos, state) -> new DisassemblerBlockEntity(Workstations.DISASSEMBLER_TYPE.get(), pos, state), DISASSEMBLER.get()).build(null));
    public static final Supplier<BlockItem> ASSEMBLER_ITEM = ITEMS.register("assembler", () -> new BlockItem(ASSEMBLER.get(), new Item.Properties()));
    public static final Supplier<BlockItem> DISASSEMBLER_ITEM = ITEMS.register("disassembler", () -> new BlockItem(DISASSEMBLER.get(), new Item.Properties()));
    static { Items.INSTANCE.registerBlock("assembler", ASSEMBLER, ASSEMBLER_ITEM); Items.INSTANCE.registerBlock("disassembler", DISASSEMBLER, DISASSEMBLER_ITEM); }
    private Workstations() {}
    private static BlockBehaviour.Properties metal() { return BlockBehaviour.Properties.of().strength(2, 5).sound(SoundType.METAL); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); TYPES.register(bus); WorkstationMenu.register(bus); bus.addListener(Workstations::capabilities);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) li.cil.oc.client.WorkstationClient.initialize(bus);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ASSEMBLER_TYPE.get(), (machine, side) -> new SidedInvWrapper(machine, side));
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, DISASSEMBLER_TYPE.get(), (machine, side) -> new SidedInvWrapper(machine, side));
    }
}
