package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.*;
import li.cil.oc.api.internal.*;
import li.cil.oc.api.network.*;
import li.cil.oc.common.item.FunctionalUpgradeItem;
import li.cil.oc.server.component.*;
import net.minecraft.world.item.ItemStack;

public final class FunctionalUpgrade extends li.cil.oc.api.prefab.DriverItem implements HostAware,EnvironmentProvider {
    @Override public boolean worksWith(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof FunctionalUpgradeItem;}
    private FunctionalUpgradeItem item(ItemStack stack){return (FunctionalUpgradeItem)stack.getItem();}
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host){
        if(!worksWith(stack))return false;
        return switch(item(stack).kind){
            case EXPERIENCE,GENERATOR,TANK->Agent.class.isAssignableFrom(host);
            case BATTERY,SOLAR->!Adapter.class.isAssignableFrom(host);
            case CHUNKLOADER->!Adapter.class.isAssignableFrom(host)&&!Tablet.class.isAssignableFrom(host);
            case ANGEL->!Adapter.class.isAssignableFrom(host)&&!Microcontroller.class.isAssignableFrom(host)&&!Tablet.class.isAssignableFrom(host);
            case HOVER,CRAFTING->li.cil.oc.api.internal.Robot.class.isAssignableFrom(host);
            case INVENTORY_CONTROLLER,TANK_CONTROLLER->Agent.class.isAssignableFrom(host)||Adapter.class.isAssignableFrom(host);
            case SIGN->Rotatable.class.isAssignableFrom(host)||Adapter.class.isAssignableFrom(host);
            case LEASH->net.minecraft.world.entity.Entity.class.isAssignableFrom(host);
            case PISTON,STICKY_PISTON,NAVIGATION->Rotatable.class.isAssignableFrom(host);
            case TRACTOR->Agent.class.isAssignableFrom(host)||Tablet.class.isAssignableFrom(host);
            default->true;
        };
    }
    @Override public String slot(ItemStack stack){return Slot.Upgrade;}
    @Override public int tier(ItemStack stack){return item(stack).tier();}
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host){
        if(host.world()!=null&&host.world().isClientSide)return null;
        return switch(item(stack).kind){
            case ANGEL->new UpgradeAngel(host);
            case BATTERY->new UpgradeBattery(host,tier(stack));
            case DATABASE->new UpgradeDatabase(host,tier(stack),stack);
            case EXPERIENCE->new UpgradeExperience(host);
            case GENERATOR->new UpgradeGenerator(host);
            case SOLAR->new UpgradeSolarGenerator(host);
            case TANK->new UpgradeTank(host);
            case HOVER->null;
            case CRAFTING->new UpgradeCrafting(host);
            case CHUNKLOADER->new UpgradeChunkloader(host);
            case INVENTORY_CONTROLLER->host instanceof li.cil.oc.api.internal.Robot?new UpgradeInventoryController.Robot(host):host instanceof Agent?new UpgradeInventoryController.Agent(host):new UpgradeInventoryController(host);
            case TANK_CONTROLLER->host instanceof Agent?new UpgradeTankController.Agent(host):new UpgradeTankController(host);
            case SIGN->host instanceof Rotatable?new UpgradeSign.InRotatable(host):host instanceof Adapter?new UpgradeSign.InAdapter(host):null;
            case LEASH->new UpgradeLeash(host);
            case PISTON->new UpgradePiston(host,false);
            case STICKY_PISTON->new UpgradePiston.Sticky(host);
            case TRACTOR->new UpgradeTractorBeam(host);
            case NAVIGATION->new UpgradeNavigation(host);
            case TRADING->new UpgradeTrading(host);
        };
    }
    @Override public Class<?> getEnvironment(ItemStack stack){if(!worksWith(stack))return null;return switch(item(stack).kind){case ANGEL->UpgradeAngel.class;case BATTERY->UpgradeBattery.class;case DATABASE->UpgradeDatabase.class;case EXPERIENCE->UpgradeExperience.class;case GENERATOR->UpgradeGenerator.class;case SOLAR->UpgradeSolarGenerator.class;case TANK->UpgradeTank.class;case HOVER->null;case CRAFTING->UpgradeCrafting.class;case CHUNKLOADER->UpgradeChunkloader.class;case INVENTORY_CONTROLLER->UpgradeInventoryController.class;case TANK_CONTROLLER->UpgradeTankController.class;case SIGN->UpgradeSign.class;case LEASH->UpgradeLeash.class;case PISTON->UpgradePiston.class;case STICKY_PISTON->UpgradePiston.Sticky.class;case TRACTOR->UpgradeTractorBeam.class;case NAVIGATION->UpgradeNavigation.class;case TRADING->UpgradeTrading.class;};}
}
