package li.cil.oc.server.driver.item;

import li.cil.oc.api.driver.EnvironmentProvider;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.common.item.NetworkCardItem;
import li.cil.oc.server.component.DataCard;
import li.cil.oc.server.component.InternetCard;
import li.cil.oc.server.component.LinkedCard;
import net.minecraft.world.item.ItemStack;

public final class NetworkCards extends li.cil.oc.api.prefab.DriverItem implements EnvironmentProvider,HostAware {
    @Override public boolean worksWith(ItemStack stack) { return !stack.isEmpty()&&stack.getItem() instanceof NetworkCardItem; }
    @Override public boolean worksWith(ItemStack stack,Class<? extends EnvironmentHost> host) {
        return worksWith(stack)&&!li.cil.oc.server.driver.Registry.INSTANCE.isBlacklistedHost(stack,host);
    }
    @Override public String slot(ItemStack stack) { return Slot.Card; }
    @Override public int tier(ItemStack stack) { return ((NetworkCardItem)stack.getItem()).tier(); }
    @Override public ManagedEnvironment createEnvironment(ItemStack stack,EnvironmentHost host) {
        if(host.world()!=null&&host.world().isClientSide) return null;
        return switch(((NetworkCardItem)stack.getItem()).kind()) {
            case INTERNET -> new InternetCard(host);
            case LINKED -> new LinkedCard(host);
            case DATA -> switch(tier(stack)) { case 0 -> new DataCard.Tier1(); case 1 -> new DataCard.Tier2(); default -> new DataCard.Tier3(); };
        };
    }
    @Override public Class<?> getEnvironment(ItemStack stack) {
        if(!worksWith(stack)) return null;
        return switch(((NetworkCardItem)stack.getItem()).kind()) {
            case INTERNET -> InternetCard.class;
            case LINKED -> LinkedCard.class;
            case DATA -> switch(tier(stack)) { case 0 -> DataCard.Tier1.class; case 1 -> DataCard.Tier2.class; default -> DataCard.Tier3.class; };
        };
    }
}
