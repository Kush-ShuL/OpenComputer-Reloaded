package li.cil.oc.common.init;

import li.cil.oc.common.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class DebugDevices {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"opencomputers");
    public static final Supplier<DebugCardItem> CARD=ITEMS.register("debugcard",()->new DebugCardItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final Supplier<DebuggerItem> DEBUGGER=ITEMS.register("debugger",()->new DebuggerItem(new Item.Properties()));
    public static final Supplier<TexturePickerItem> TEXTURE_PICKER=ITEMS.register("texturepicker",()->new TexturePickerItem(new Item.Properties()));
    static{Items.INSTANCE.registerItem("debugcard",CARD);Items.INSTANCE.registerItem("debugger",DEBUGGER);Items.INSTANCE.registerItem("texturepicker",TEXTURE_PICKER);}
    private DebugDevices(){}
    public static void register(IEventBus bus){ITEMS.register(bus);bus.addListener(li.cil.oc.common.network.DebugNetworking::register);NeoForge.EVENT_BUS.addListener(li.cil.oc.server.component.DebugAccess::commands);NeoForge.EVENT_BUS.addListener(DebuggerItem::stop);}
    public static void initializeDrivers(){var card=new li.cil.oc.server.driver.item.DebugCard();li.cil.oc.api.Driver.add((li.cil.oc.api.driver.DriverItem)card);li.cil.oc.api.Driver.add((li.cil.oc.api.driver.EnvironmentProvider)card);}
}
