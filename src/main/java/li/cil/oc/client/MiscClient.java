package li.cil.oc.client;

import li.cil.oc.client.renderer.HoverBootsModel;
import li.cil.oc.common.init.MiscDevices;
import li.cil.oc.util.ItemColorizer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Physical-client only armor and inventory tint registration. */
public final class MiscClient {
    private MiscClient() {}
    public static void register(IEventBus bus) {
        bus.addListener(MiscClient::extensions);
        bus.addListener(MiscClient::colors);
    }
    private static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private HoverBootsModel model;
            @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity wearer, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (slot != EquipmentSlot.FEET) return original;
                if (model == null) model = new HoverBootsModel();
                model.lightColor = ItemColorizer.hasColor(stack) ? ItemColorizer.getColor(stack) : 0x66dd55;
                return model;
            }
        }, MiscDevices.HOVER_BOOTS.get());
    }
    private static void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 1 ? 0xff000000 | (ItemColorizer.hasColor(stack) ? ItemColorizer.getColor(stack) : 0x66dd55) : -1, MiscDevices.HOVER_BOOTS.get());
    }
}
