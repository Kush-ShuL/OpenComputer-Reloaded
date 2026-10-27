package li.cil.oc.common.item;

import java.util.List;
import li.cil.oc.common.item.data.MicrocontrollerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** The assembled world block item; the independent empty case items are unchanged. */
public final class MicrocontrollerBlockItem extends BlockItem {
    public MicrocontrollerBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book){return false;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);for(String line:Component.translatable("oc:tooltip.microcontroller").getString().split("\\[nl\\]"))tooltip.add(Component.literal(line));
        if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT&&ClientInput.extended()&&context.registries()!=null)for(ItemStack part:new MicrocontrollerData(stack,context.registries()).components)if(!part.isEmpty())tooltip.add(Component.literal("- ").append(part.getHoverName()));
    }
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static final class ClientInput{
        static boolean extended(){var client=net.minecraft.client.Minecraft.getInstance();var key=client.options.keyShift.getKey();long window=client.getWindow().getWindow();return switch(key.getType()){case MOUSE->org.lwjgl.glfw.GLFW.glfwGetMouseButton(window,key.getValue())==org.lwjgl.glfw.GLFW.GLFW_PRESS;case KEYSYM->key.getValue()>=0&&com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,key.getValue());default->false;};}
    }
}
