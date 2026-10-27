package li.cil.oc.common.init;

import java.util.List;
import java.util.function.Supplier;
import li.cil.oc.common.block.*;
import li.cil.oc.common.block.entity.*;
import li.cil.oc.common.menu.RelayMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The original relay (switch/access point), network splitter and waypoint. */
public final class NetworkBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"opencomputers");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"opencomputers");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"opencomputers");
    public static final Supplier<RelayBlock> RELAY=BLOCKS.register("relay",()->new RelayBlock(metal(),()->NetworkBlocks.RELAY_TYPE.get()));
    public static final Supplier<NetSplitterBlock> NET_SPLITTER=BLOCKS.register("netsplitter",()->new NetSplitterBlock(metal().noOcclusion(),()->NetworkBlocks.NET_SPLITTER_TYPE.get()));
    public static final Supplier<WaypointBlock> WAYPOINT=BLOCKS.register("waypoint",()->new WaypointBlock(metal().isValidSpawn((state,level,pos,type)->false),()->NetworkBlocks.WAYPOINT_TYPE.get()));
    public static final Supplier<BlockEntityType<RelayBlockEntity>> RELAY_TYPE=TYPES.register("relay",()->BlockEntityType.Builder.of((pos,state)->new RelayBlockEntity(NetworkBlocks.RELAY_TYPE.get(),pos,state),RELAY.get()).build(null));
    public static final Supplier<BlockEntityType<NetSplitterBlockEntity>> NET_SPLITTER_TYPE=TYPES.register("netsplitter",()->BlockEntityType.Builder.of((pos,state)->new NetSplitterBlockEntity(NetworkBlocks.NET_SPLITTER_TYPE.get(),pos,state),NET_SPLITTER.get()).build(null));
    public static final Supplier<BlockEntityType<WaypointBlockEntity>> WAYPOINT_TYPE=TYPES.register("waypoint",()->BlockEntityType.Builder.of((pos,state)->new WaypointBlockEntity(NetworkBlocks.WAYPOINT_TYPE.get(),pos,state),WAYPOINT.get()).build(null));
    public static final Supplier<MenuType<RelayMenu>> RELAY_MENU=MENUS.register("relay",()->IMenuTypeExtension.create((id,inventory,data)->new RelayMenu(id,inventory,data.readBlockPos())));
    public static final List<Supplier<? extends Block>> ALL_BLOCKS=List.of(RELAY,NET_SPLITTER,WAYPOINT);
    public static final List<Supplier<? extends Item>> ALL_ITEMS=List.of(item("relay",RELAY),item("netsplitter",NET_SPLITTER),item("waypoint",WAYPOINT));
    private static BlockBehaviour.Properties metal(){return BlockBehaviour.Properties.of().strength(2,5).sound(SoundType.METAL);}
    private static Supplier<? extends Item> item(String name,Supplier<? extends Block> block){var item=ITEMS.register(name,()->new BlockItem(block.get(),new Item.Properties()){@Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);tooltip.add(Component.translatable("oc:tooltip."+name));}});Items.INSTANCE.registerBlock(name,block,item);return item;}
    private NetworkBlocks(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);MENUS.register(bus);bus.addListener(li.cil.oc.common.network.NetworkBlockNetworking::register);NeoForge.EVENT_BUS.register(li.cil.oc.server.network.Waypoints.class);if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)li.cil.oc.client.NetworkBlockClient.register(bus);}
    public static void initializeDrivers(){}
}
