package li.cil.oc.common.init;

import li.cil.oc.common.item.NetworkCardItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NetworkCards {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"opencomputers");
    private static boolean initialized;
    static {
        item("internetcard",NetworkCardItem.Kind.INTERNET,1);
        item("linkedcard",NetworkCardItem.Kind.LINKED,2);
        for(int tier=0;tier<3;tier++) item("datacard"+(tier+1),NetworkCardItem.Kind.DATA,tier);
    }
    private NetworkCards() {}
    private static void item(String name,NetworkCardItem.Kind kind,int tier) {
        var holder=ITEMS.register(name,()->new NetworkCardItem(new Item.Properties(),kind,tier));
        Items.INSTANCE.registerItem(name,holder::get);
    }
    public static void register(IEventBus bus) { ITEMS.register(bus); }
    public static synchronized void initializeDrivers() {
        if(initialized) return;
        var driver=new li.cil.oc.server.driver.item.NetworkCards();
        li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)driver);
        li.cil.oc.api.Driver.add((li.cil.oc.api.driver.EnvironmentProvider)driver);
        initialized=true;
    }
}
