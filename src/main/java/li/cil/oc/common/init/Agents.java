package li.cil.oc.common.init;

import java.util.function.Supplier;
import li.cil.oc.common.block.*;
import li.cil.oc.common.block.entity.RobotBlockEntity;
import li.cil.oc.common.entity.DroneEntity;
import li.cil.oc.common.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Original agent devices and structural upgrades, independently registered. */
public final class Agents {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,"opencomputers");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"opencomputers");
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"opencomputers");
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,"opencomputers");
    public static final Supplier<RobotBlock> ROBOT=BLOCKS.register("robot",()->new RobotBlock(BlockBehaviour.Properties.of().strength(2,10).sound(SoundType.METAL).noOcclusion()));
    public static final Supplier<RobotAfterimageBlock> AFTERIMAGE=BLOCKS.register("robotafterimage",()->new RobotAfterimageBlock(BlockBehaviour.Properties.of().strength(-1).noOcclusion().noLootTable().replaceable()));
    public static final Supplier<BlockEntityType<RobotBlockEntity>> ROBOT_TYPE=TYPES.register("robot",()->BlockEntityType.Builder.of((pos,state)->new RobotBlockEntity(Agents.ROBOT_TYPE.get(),pos,state),ROBOT.get()).build(null));
    public static final Supplier<BlockItem> ROBOT_ITEM=ITEMS.register("robot",()->new li.cil.oc.common.item.RobotItem(ROBOT.get(),new Item.Properties()));
    public static final Supplier<EntityType<DroneEntity>> DRONE_TYPE=ENTITIES.register("drone",()->EntityType.Builder.<DroneEntity>of(DroneEntity::new,MobCategory.MISC).sized(.75f,.375f).fireImmune().clientTrackingRange(8).updateInterval(3).build("opencomputers:drone"));
    public static final Supplier<DroneItem> DRONE=ITEMS.register("drone",()->new DroneItem(new Item.Properties()));
    static {
        Items.INSTANCE.registerBlock("robot",ROBOT,ROBOT_ITEM);Items.INSTANCE.registerItem("drone",DRONE);
        for(int index=0;index<3;index++){int tier=index==2?3:index;String name=index==2?"dronecasecreative":"dronecase"+(index+1);var item=ITEMS.register(name,()->new AgentCaseItem(new Item.Properties(),tier));Items.INSTANCE.registerItem(name,item);}
        upgrade("inventoryupgrade","inventory",0);
        for(int tier=0;tier<3;tier++){upgrade("cardcontainer"+(tier+1),li.cil.oc.api.driver.item.Slot.Card,tier);upgrade("upgradecontainer"+(tier+1),li.cil.oc.api.driver.item.Slot.Upgrade,tier);}
        upgrade("floppycontainer",li.cil.oc.api.driver.item.Slot.Floppy,0);
    }
    private static void upgrade(String name,String kind,int tier){var item=ITEMS.register(name,()->new AgentUpgradeItem(new Item.Properties(),kind,tier));Items.INSTANCE.registerItem(name,item);}
    private Agents(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);ENTITIES.register(bus);li.cil.oc.common.menu.AgentMenu.register(bus);bus.addListener(li.cil.oc.common.network.AgentNetworking::register);if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)li.cil.oc.client.AgentClient.register(bus);}
    public static void initializeDrivers(){li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)new li.cil.oc.server.driver.item.InventoryUpgrade());li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)new li.cil.oc.server.driver.item.AgentUpgrade());var screen=new li.cil.oc.server.driver.item.AgentScreen();li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)screen);li.cil.oc.api.Driver.add((li.cil.oc.api.driver.EnvironmentProvider)screen);var keyboard=new li.cil.oc.server.driver.item.AgentKeyboard();li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)keyboard);li.cil.oc.api.Driver.add((li.cil.oc.api.driver.EnvironmentProvider)keyboard);}
}
