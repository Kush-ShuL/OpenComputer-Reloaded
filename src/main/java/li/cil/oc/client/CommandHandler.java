package li.cil.oc.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import li.cil.oc.client.renderer.WirelessNetworkDebugRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Physical-client commands from the original client CommandHandler. */
public final class CommandHandler {
    private static boolean registered;
    private CommandHandler(){}
    public static void register(){if(registered)return;registered=true;NeoForge.EVENT_BUS.addListener(CommandHandler::commands);NeoForge.EVENT_BUS.addListener(WirelessNetworkDebugRenderer::render);}
    private static void commands(RegisterClientCommandsEvent event){
        event.getDispatcher().register(Commands.literal("oc_setclipboard")
                .executes(context->0)
                .then(Commands.argument("value",StringArgumentType.string())
                        .executes(context->clipboard(StringArgumentType.getString(context,"value")))
                        .then(Commands.argument("ignored",StringArgumentType.greedyString())
                                .executes(context->clipboard(StringArgumentType.getString(context,"value"))))));
    }
    private static int clipboard(String value){Minecraft.getInstance().keyboardHandler.setClipboard(value);return 1;}
}
