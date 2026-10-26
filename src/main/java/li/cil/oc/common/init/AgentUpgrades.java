package li.cil.oc.common.init;

import li.cil.oc.Settings;
import li.cil.oc.api.event.*;
import li.cil.oc.api.internal.*;
import li.cil.oc.api.network.Node;
import li.cil.oc.common.item.FunctionalUpgradeItem;
import li.cil.oc.common.item.FunctionalUpgradeItem.Kind;
import li.cil.oc.server.component.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Consumer;

/** Built-in upgrade registration and the original robot event effects. */
public final class AgentUpgrades {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"opencomputers");
    static {
        item("angelupgrade",Kind.ANGEL,1);for(int tier=0;tier<3;tier++){item("batteryupgrade"+(tier+1),Kind.BATTERY,tier);item("databaseupgrade"+(tier+1),Kind.DATABASE,tier);}
        item("experienceupgrade",Kind.EXPERIENCE,2);item("generatorupgrade",Kind.GENERATOR,1);item("hoverupgrade1",Kind.HOVER,0);item("hoverupgrade2",Kind.HOVER,1);item("solargeneratorupgrade",Kind.SOLAR,1);item("tankupgrade",Kind.TANK,0);item("craftingupgrade",Kind.CRAFTING,1);item("chunkloaderupgrade",Kind.CHUNKLOADER,2);item("inventorycontrollerupgrade",Kind.INVENTORY_CONTROLLER,1);item("tankcontrollerupgrade",Kind.TANK_CONTROLLER,1);item("signupgrade",Kind.SIGN,0);item("leashupgrade",Kind.LEASH,0);item("pistonupgrade",Kind.PISTON,0);item("stickypistonupgrade",Kind.STICKY_PISTON,0);item("tractorbeamupgrade",Kind.TRACTOR,2);item("navigationupgrade",Kind.NAVIGATION,1);item("tradingupgrade",Kind.TRADING,1);
    }
    private static void item(String name,Kind kind,int tier){var item=ITEMS.register(name,()->new FunctionalUpgradeItem(new Item.Properties(),kind,tier));Items.INSTANCE.registerItem(name,item);}
    private AgentUpgrades(){}
    public static void register(IEventBus bus){ITEMS.register(bus);li.cil.oc.common.menu.DatabaseMenu.register(bus);if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT)li.cil.oc.client.AgentUpgradeClient.register(bus);bus.addListener(UpgradeChunkloader::registerTickets);NeoForge.EVENT_BUS.addListener(UpgradeChunkloader::saveWorld);NeoForge.EVENT_BUS.addListener(AgentUpgrades::placeInAir);NeoForge.EVENT_BUS.addListener(AgentUpgrades::movePre);NeoForge.EVENT_BUS.addListener(AgentUpgrades::toolDamage);NeoForge.EVENT_BUS.addListener(AgentUpgrades::breakPre);NeoForge.EVENT_BUS.addListener(AgentUpgrades::breakPost);NeoForge.EVENT_BUS.addListener(AgentUpgrades::attackPost);NeoForge.EVENT_BUS.addListener(AgentUpgrades::placePost);NeoForge.EVENT_BUS.addListener(AgentUpgrades::movePost);NeoForge.EVENT_BUS.addListener(AgentUpgrades::exhaustion);}
    public static void initializeDrivers(){var driver=new li.cil.oc.server.driver.item.FunctionalUpgrade();li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)driver);li.cil.oc.api.Driver.add((li.cil.oc.api.driver.EnvironmentProvider)driver);var barcode=new li.cil.oc.server.driver.item.BarcodeReader();li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)barcode);li.cil.oc.api.Driver.add((li.cil.oc.api.driver.EnvironmentProvider)barcode);}
    private static void upgrades(Agent agent,Consumer<UpgradeExperience> action){for(Node node:agent.machine().node().reachableNodes())if(node.host() instanceof UpgradeExperience upgrade)action.accept(upgrade);}
    private static int level(Agent agent){int[] level={0};upgrades(agent,upgrade->level[0]+=upgrade.level());return level[0];}
    private static void add(Agent agent,double amount){upgrades(agent,upgrade->upgrade.addExperience(amount));}
    private static void placeInAir(RobotPlaceInAirEvent event){for(Node node:event.agent.machine().node().reachableNodes())if(node.host() instanceof UpgradeAngel){event.setAllowed(true);break;}}
    private static void toolDamage(RobotUsedToolEvent.ComputeDamageRate event){event.setDamageRate(event.getDamageRate()*Math.max(0,1-level(event.agent)*Settings.get().toolEfficiencyPerLevel));}
    private static void breakPre(RobotBreakBlockEvent.Pre event){event.setBreakTime(event.getBreakTime()*Math.max(0,1-level(event.agent)*Settings.get().harvestSpeedBoostPerLevel));}
    private static void breakPost(RobotBreakBlockEvent.Post event){add(event.agent,event.experience*Settings.get().robotOreXpRate+Settings.get().robotActionXp);}
    private static void attackPost(RobotAttackEntityEvent.Post event){if(event.agent instanceof li.cil.oc.api.internal.Robot&&!event.target.isAlive())add(event.agent,Settings.get().robotActionXp);}
    private static void placePost(RobotPlaceBlockEvent.Post event){add(event.agent,Settings.get().robotActionXp);}
    private static void movePost(RobotMoveEvent.Post event){add(event.agent,Settings.get().robotExhaustionXpRate*.01);for(Node node:event.agent.machine().node().reachableNodes())if(node.host() instanceof UpgradeChunkloader loader)loader.updateLoadedChunks();}
    private static void exhaustion(RobotExhaustionEvent event){add(event.agent,Settings.get().robotExhaustionXpRate*event.exhaustion);}
    private static void movePre(RobotMoveEvent.Pre event){if(!(event.agent instanceof li.cil.oc.api.internal.Robot robot)||Settings.get().limitFlightHeight<0)return;int maximum=Settings.get().limitFlightHeight;for(ItemStack stack:robot.internalComponents())if(stack.getItem() instanceof FunctionalUpgradeItem item&&item.kind==Kind.HOVER)maximum=Math.max(maximum,Settings.get().upgradeFlightHeight[item.tier()]);for(int i=0;i<robot.equipmentInventory().getContainerSize();i++){ItemStack stack=robot.equipmentInventory().getItem(i);if(stack.getItem() instanceof FunctionalUpgradeItem item&&item.kind==Kind.HOVER)maximum=Math.max(maximum,Settings.get().upgradeFlightHeight[item.tier()]);}if(event.direction==Direction.DOWN||maximum>=robot.world().getHeight())return;BlockPos start=BlockPos.containing(robot.xPosition(),robot.yPosition(),robot.zPosition()),target=start.relative(event.direction);for(Direction direction:Direction.values())if(robot.world().getBlockState(start.relative(direction)).isFaceSturdy(robot.world(),start.relative(direction),direction.getOpposite())||robot.world().getBlockState(target.relative(direction)).isFaceSturdy(robot.world(),target.relative(direction),direction.getOpposite()))return;for(int down=1;down<=maximum;down++)if(!robot.world().isEmptyBlock(start.below(down)))return;event.setCanceled(true);}
}
