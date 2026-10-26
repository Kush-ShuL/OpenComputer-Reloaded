package li.cil.oc.common.init;

import java.util.function.Supplier;
import li.cil.oc.common.item.RackDiskDriveItem;
import li.cil.oc.common.menu.RackDiskMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The original single-floppy rack mountable, including its held-item inventory. */
public final class RackDisks {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"opencomputers");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"opencomputers");
    public static final Supplier<RackDiskDriveItem> ITEM=ITEMS.register("diskdrivemountable",()->new RackDiskDriveItem(new Item.Properties()));
    public static final Supplier<MenuType<RackDiskMenu>> MENU=MENUS.register("diskdrivemountable",()->IMenuTypeExtension.create(RackDiskMenu::new));
    private static boolean initialized;
    static{Items.INSTANCE.registerItem("diskdrivemountable",ITEM);}
    private RackDisks(){}
    public static void register(IEventBus bus){ITEMS.register(bus);MENUS.register(bus);if(FMLEnvironment.dist==Dist.CLIENT)li.cil.oc.client.RackDiskClient.initialize(bus);}
    public static synchronized void initializeDrivers(){if(initialized)return;li.cil.oc.api.Driver.add(new li.cil.oc.server.driver.item.DiskDriveMountable());initialized=true;}
}
