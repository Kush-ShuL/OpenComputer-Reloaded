package li.cil.oc.common.init;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import li.cil.oc.common.block.RackBlock;
import li.cil.oc.common.block.entity.RackBlockEntity;
import li.cil.oc.common.item.ComponentBusItem;
import li.cil.oc.common.item.ServerItem;
import li.cil.oc.common.item.TerminalItem;
import li.cil.oc.common.menu.RackMenu;
import li.cil.oc.common.menu.ServerMenu;
import li.cil.oc.common.network.RackNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Racks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"opencomputers");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"opencomputers");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"opencomputers");
    public static final Supplier<RackBlock> RACK=BLOCKS.register("rack",()->new RackBlock(BlockBehaviour.Properties.of().strength(2,5).sound(SoundType.METAL).noOcclusion(),()->Racks.RACK_TYPE.get()));
    public static final Supplier<BlockEntityType<RackBlockEntity>> RACK_TYPE=TYPES.register("rack",()->BlockEntityType.Builder.of((pos,state)->new RackBlockEntity(Racks.RACK_TYPE.get(),pos,state),RACK.get()).build(null));
    public static final Supplier<BlockItem> RACK_ITEM=ITEMS.register("rack",()->new BlockItem(RACK.get(),new Item.Properties()));
    public static final Supplier<Item> TERMINAL_SERVER=ITEMS.register("terminalserver",()->new Item(new Item.Properties().stacksTo(1)));
    public static final Supplier<TerminalItem> TERMINAL=ITEMS.register("terminal",()->new TerminalItem(new Item.Properties()));
    public static final List<Supplier<ServerItem>> SERVERS=new ArrayList<>();
    public static final List<Supplier<ComponentBusItem>> BUSES=new ArrayList<>();
    public static final Supplier<MenuType<RackMenu>> RACK_MENU=MENUS.register("rack",()->IMenuTypeExtension.create((id,inventory,data)->new RackMenu(id,inventory,data.readBlockPos())));
    public static final Supplier<MenuType<ServerMenu>> SERVER_MENU=MENUS.register("server",()->IMenuTypeExtension.create((id,inventory,data)->{boolean mounted=data.readBoolean();int tier=Math.clamp(data.readVarInt(),0,3);return mounted?new ServerMenu(id,inventory,true,tier,data.readBlockPos(),data.readVarInt(),InteractionHand.MAIN_HAND):new ServerMenu(id,inventory,false,tier,BlockPos.ZERO,-1,data.readEnum(InteractionHand.class));}));
    private static boolean driversInitialized;
    static {
        Items.INSTANCE.registerBlock("rack",RACK,RACK_ITEM);Items.INSTANCE.registerItem("terminalserver",TERMINAL_SERVER);Items.INSTANCE.registerItem("terminal",TERMINAL);
        for(int i=0;i<4;i++){int tier=i;String server=tier==3?"servercreative":"server"+(tier+1),bus=tier==3?"componentbuscreative":"componentbus"+(tier+1);Supplier<ServerItem> blade=ITEMS.register(server,()->new ServerItem(new Item.Properties(),tier));Supplier<ComponentBusItem> componentBus=ITEMS.register(bus,()->new ComponentBusItem(new Item.Properties(),tier));SERVERS.add(blade);BUSES.add(componentBus);Items.INSTANCE.registerItem(server,blade);Items.INSTANCE.registerItem(bus,componentBus);}
    }
    private Racks(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);MENUS.register(bus);bus.addListener(RackNetworking::register);bus.addListener(Racks::capabilities);if(FMLEnvironment.dist==Dist.CLIENT)li.cil.oc.client.RackClient.initialize(bus);}
    private static void capabilities(RegisterCapabilitiesEvent event){event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,RACK_TYPE.get(),(rack,side)->new InvWrapper(rack));event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,RACK_TYPE.get(),(rack,side)->rack.energy(side));}
    public static synchronized void initializeDrivers(){if(driversInitialized)return;li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.item.RackMountableDriver());li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.item.ComponentBus());li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.item.ServerInventoryProvider());driversInitialized=true;}
}
