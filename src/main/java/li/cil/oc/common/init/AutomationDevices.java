package li.cil.oc.common.init;

import li.cil.oc.common.block.*;
import li.cil.oc.common.block.entity.*;
import li.cil.oc.common.network.AutomationNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.List;
import java.util.function.Supplier;

/** Independent bootstrap for the original working transposer, geolyzer and motion sensor. */
public final class AutomationDevices {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"opencomputers");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"opencomputers");
    public static final Supplier<TransposerBlock> TRANSPOSER=BLOCKS.register("transposer",()->new TransposerBlock(metal().noOcclusion(),()->AutomationDevices.TRANSPOSER_TYPE.get()));
    public static final Supplier<BlockEntityType<TransposerBlockEntity>> TRANSPOSER_TYPE=TYPES.register("transposer",()->BlockEntityType.Builder.of((pos,state)->new TransposerBlockEntity(AutomationDevices.TRANSPOSER_TYPE.get(),pos,state),TRANSPOSER.get()).build(null));
    public static final Supplier<GeolyzerBlock> GEOLYZER=BLOCKS.register("geolyzer",()->new GeolyzerBlock(metal(),()->AutomationDevices.GEOLYZER_TYPE.get()));
    public static final Supplier<BlockEntityType<GeolyzerBlockEntity>> GEOLYZER_TYPE=TYPES.register("geolyzer",()->BlockEntityType.Builder.of((pos,state)->new GeolyzerBlockEntity(AutomationDevices.GEOLYZER_TYPE.get(),pos,state),GEOLYZER.get()).build(null));
    public static final Supplier<MotionSensorBlock> MOTION_SENSOR=BLOCKS.register("motionsensor",()->new MotionSensorBlock(metal(),()->AutomationDevices.MOTION_SENSOR_TYPE.get()));
    public static final Supplier<BlockEntityType<MotionSensorBlockEntity>> MOTION_SENSOR_TYPE=TYPES.register("motionsensor",()->BlockEntityType.Builder.of((pos,state)->new MotionSensorBlockEntity(AutomationDevices.MOTION_SENSOR_TYPE.get(),pos,state),MOTION_SENSOR.get()).build(null));
    public static final List<Supplier<? extends Block>> ALL_BLOCKS=List.of(TRANSPOSER,GEOLYZER,MOTION_SENSOR);
    public static final List<Supplier<? extends Item>> ALL_ITEMS=List.of(item("transposer",TRANSPOSER),item("geolyzer",GEOLYZER),item("motionsensor",MOTION_SENSOR));
    private AutomationDevices(){}
    private static BlockBehaviour.Properties metal(){return BlockBehaviour.Properties.of().strength(2,5).sound(SoundType.METAL).isValidSpawn((state,world,pos,type)->false);}
    private static Supplier<? extends Item> item(String name,Supplier<? extends Block> block){
        var item=ITEMS.register(name,()->new BlockItem(block.get(),new Item.Properties()){
            @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);tooltip.add(Component.translatable("oc:tooltip."+name));}
        });Items.INSTANCE.registerBlock(name,block,item);return item;
    }
    public static void register(IEventBus modBus){
        BLOCKS.register(modBus);ITEMS.register(modBus);TYPES.register(modBus);modBus.addListener(AutomationNetworking::register);
        if(FMLEnvironment.dist==Dist.CLIENT)li.cil.oc.client.AutomationDeviceClient.register(modBus);
    }
}
