package li.cil.oc.common.init;

import java.util.List;
import java.util.function.Supplier;
import li.cil.oc.common.block.MicrocontrollerBlock;
import li.cil.oc.common.block.entity.MicrocontrollerBlockEntity;
import li.cil.oc.common.item.*;
import li.cil.oc.common.item.data.MicrocontrollerData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Assembled tablets and microcontrollers, using the original item data contracts. */
public final class PortableDevices {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"opencomputers");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"opencomputers");
    public static final Supplier<MicrocontrollerBlock> MICROCONTROLLER=BLOCKS.register("microcontroller",()->new MicrocontrollerBlock(BlockBehaviour.Properties.of().strength(2,5).sound(SoundType.METAL)));
    public static final Supplier<BlockEntityType<MicrocontrollerBlockEntity>> MICROCONTROLLER_TYPE=TYPES.register("microcontroller",()->BlockEntityType.Builder.of((pos,state)->new MicrocontrollerBlockEntity(PortableDevices.MICROCONTROLLER_TYPE.get(),pos,state),MICROCONTROLLER.get()).build(null));
    public static final Supplier<BlockItem> MICROCONTROLLER_ITEM=ITEMS.register("microcontroller",()->new BlockItem(MICROCONTROLLER.get(),new Item.Properties().stacksTo(1)){
        @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);for(String line:Component.translatable("oc:tooltip.microcontroller").getString().split("\\[nl\\]"))tooltip.add(Component.literal(line));if(flag.isAdvanced()&&context.registries()!=null)for(ItemStack part:new MicrocontrollerData(stack,context.registries()).components)if(!part.isEmpty())tooltip.add(Component.literal("- ").append(part.getHoverName()));}
    });
    public static final Supplier<TabletItem> TABLET=ITEMS.register("tablet",()->new TabletItem(new Item.Properties()));
    public static final Supplier<TabletCaseItem> TABLET_CASE_1=tabletCase("tabletcase1",0),TABLET_CASE_2=tabletCase("tabletcase2",1),TABLET_CASE_CREATIVE=tabletCase("tabletcasecreative",3);
    public static final Supplier<MicrocontrollerCaseItem> MICROCONTROLLER_CASE_1=microCase("microcontrollercase1",0),MICROCONTROLLER_CASE_2=microCase("microcontrollercase2",1),MICROCONTROLLER_CASE_CREATIVE=microCase("microcontrollercasecreative",3);
    public static final List<Supplier<? extends Block>> ALL_BLOCKS=List.of(MICROCONTROLLER);
    public static final List<Supplier<? extends Item>> ALL_ITEMS=List.of(TABLET_CASE_1,TABLET_CASE_2,TABLET_CASE_CREATIVE,MICROCONTROLLER_CASE_1,MICROCONTROLLER_CASE_2,MICROCONTROLLER_CASE_CREATIVE);
    static{Items.INSTANCE.registerBlock("microcontroller",MICROCONTROLLER,MICROCONTROLLER_ITEM);Items.INSTANCE.registerItem("tablet",TABLET);}
    private PortableDevices(){}
    private static Supplier<TabletCaseItem> tabletCase(String name,int tier){var item=ITEMS.register(name,()->new TabletCaseItem(new Item.Properties(),tier));Items.INSTANCE.registerItem(name,item);return item;}
    private static Supplier<MicrocontrollerCaseItem> microCase(String name,int tier){var item=ITEMS.register(name,()->new MicrocontrollerCaseItem(new Item.Properties(),tier));Items.INSTANCE.registerItem(name,item);return item;}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);li.cil.oc.common.menu.TabletMenu.register(bus);li.cil.oc.common.menu.MicrocontrollerMenu.register(bus);bus.addListener(li.cil.oc.common.network.PortableNetworking::register);li.cil.oc.server.portable.TabletManager.register();li.cil.oc.common.network.PortableNetworking.registerEvents();if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)li.cil.oc.client.PortableClient.register(bus);}
    public static void initializeDrivers(){var screen=new li.cil.oc.server.driver.item.TabletScreen();var keyboard=new li.cil.oc.server.driver.item.TabletKeyboard();li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)screen);li.cil.oc.api.Driver.add((li.cil.oc.api.driver.EnvironmentProvider)screen);li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)keyboard);li.cil.oc.api.Driver.add((li.cil.oc.api.driver.EnvironmentProvider)keyboard);li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)new li.cil.oc.server.driver.item.TabletContainer());li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)new li.cil.oc.server.driver.item.TabletStorage());li.cil.oc.common.integration.ItemCharge.add(new li.cil.oc.common.integration.ItemCharge.Handler("opencomputers:tablet",stack->stack.getItem() instanceof TabletItem,(stack,amount,simulate)->((TabletItem)stack.getItem()).charge(stack,amount,simulate)));}
}
