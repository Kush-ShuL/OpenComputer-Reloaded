package li.cil.oc.client;

import li.cil.oc.OpenComputers;
import li.cil.oc.api.internal.Colored;
import li.cil.oc.common.block.CaseBlock;
import li.cil.oc.common.block.ScreenBlock;
import li.cil.oc.common.init.Blocks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import java.util.ArrayList;

@EventBusSubscriber(modid = OpenComputers.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ColorHandlers {
    private ColorHandlers() {}
    @SubscribeEvent public static void blocks(RegisterColorHandlersEvent.Block event) {
        var blocks = new ArrayList<net.minecraft.world.level.block.Block>();
        for (var block : Blocks.CASES) blocks.add(block.get());
        for (var block : Blocks.SCREENS) blocks.add(block.get());
        blocks.add(Blocks.CABLE.get());
        event.register((state, level, pos, index) -> index == 0 && level != null && pos != null && level.getBlockEntity(pos) instanceof Colored colored ? colored.getColor() : -1,
                blocks.toArray(net.minecraft.world.level.block.Block[]::new));
    }
    @SubscribeEvent public static void items(RegisterColorHandlersEvent.Item event) {
        var items = new ArrayList<net.minecraft.world.level.ItemLike>();
        for (var block : Blocks.CASES) items.add(block.get());
        for (var block : Blocks.SCREENS) items.add(block.get());
        items.add(Blocks.CABLE.get());
        event.register((stack, index) -> {
            if (index != 0) return -1;
            var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (data.contains("oc:color")) return data.getInt("oc:color");
            if (stack.getItem() instanceof BlockItem item) {
                if (item.getBlock() instanceof CaseBlock block) return new int[]{0xABABAB, 0xFFFF66, 0x66FFFF, 0xC354CD}[block.tier];
                if (item.getBlock() instanceof ScreenBlock block) return new int[]{0xABABAB, 0xFFFF66, 0x66FFFF}[block.tier];
            }
            return 0xABABAB;
        }, items.toArray(net.minecraft.world.level.ItemLike[]::new));
    }
}
