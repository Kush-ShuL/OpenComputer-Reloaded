package li.cil.oc.common.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** The original assembled-device component list uses the player's configured sneak key. */
@OnlyIn(Dist.CLIENT)
final class RobotDroneTooltips {
    private RobotDroneTooltips(){}
    static boolean extended(){
        var client=net.minecraft.client.Minecraft.getInstance();var key=client.options.keyShift.getKey();long window=client.getWindow().getWindow();
        return switch(key.getType()){
            case MOUSE -> org.lwjgl.glfw.GLFW.glfwGetMouseButton(window,key.getValue())==org.lwjgl.glfw.GLFW.GLFW_PRESS;
            case KEYSYM -> key.getValue()>=0&&com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,key.getValue());
            default -> false;
        };
    }
    static void components(ItemStack[] components,List<Component> tooltip){for(ItemStack component:components)if(!component.isEmpty())tooltip.add(Component.literal("- ").append(component.getHoverName()));}
}
