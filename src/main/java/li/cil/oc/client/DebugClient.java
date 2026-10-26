package li.cil.oc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.UseOnContext;

public final class DebugClient {
    private DebugClient(){}
    public static void pickTexture(UseOnContext context){var model=Minecraft.getInstance().getBlockRenderer().getBlockModel(context.getLevel().getBlockState(context.getClickedPos()));var texture=model.getParticleIcon(context.getLevel().getModelDataManager().getAt(context.getClickedPos()));if(texture!=null)context.getPlayer().sendSystemMessage(Component.translatable("oc:gui.Chat.TextureName",texture.contents().name().toString()));}
    public static void clipboard(String text){Minecraft.getInstance().keyboardHandler.setClipboard(text);}
}
