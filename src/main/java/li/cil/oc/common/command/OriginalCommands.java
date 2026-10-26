package li.cil.oc.common.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import li.cil.oc.api.Nanomachines;
import li.cil.oc.api.Network;
import li.cil.oc.common.nanomachines.ControllerImpl;
import li.cil.oc.common.nanomachines.NanomachineNetwork;
import li.cil.oc.server.network.DebugNetwork;
import li.cil.oc.server.network.WirelessNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.List;
import java.util.Map;

/** Remaining original operator commands. Runtime debug flags are deliberately not saved to settings. */
public final class OriginalCommands {
    private static volatile boolean wirelessRendering;
    private static volatile Map<ResourceKey<Level>,List<WirelessNetwork.DebugBound>> wirelessBounds=Map.of();
    private static boolean registered;
    private OriginalCommands(){}

    public static void register(IEventBus bus){
        if(registered)return;
        registered=true;
        bus.addListener(CommandNetworking::register);
        NeoForge.EVENT_BUS.addListener(OriginalCommands::commands);
        NeoForge.EVENT_BUS.addListener(OriginalCommands::tick);
        NeoForge.EVENT_BUS.addListener(OriginalCommands::stopped);
    }

    /** The original SimpleCommand permits level-two commands in an integrated singleplayer server. */
    public static boolean operator(CommandSourceStack source){return source.hasPermission(2)||source.getServer().isSingleplayer();}

    private static void commands(RegisterCommandsEvent event){
        for(String name:new String[]{"oc_debugNanomachines","oc_dn"})
            event.getDispatcher().register(Commands.literal(name).requires(OriginalCommands::operator)
                    .executes(context->debugNanomachines(context.getSource())));
        for(String name:new String[]{"oc_nanomachines","oc_nm"})
            event.getDispatcher().register(Commands.literal(name).requires(OriginalCommands::operator)
                    .executes(context->printNanomachines(context.getSource(),null))
                    .then(Commands.argument("player",StringArgumentType.word())
                            .suggests((context,builder)->SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(),builder))
                            .executes(context->printNanomachines(context.getSource(),StringArgumentType.getString(context,"player")))));
        for(String name:new String[]{"oc_sendDebugMessage","oc_sdbg"})
            event.getDispatcher().register(Commands.literal(name).requires(OriginalCommands::operator)
                    .executes(context->{throw failure("no destination address specified.");})
                    .then(Commands.argument("destinationAddress",StringArgumentType.string())
                            .executes(context->sendDebugMessage(context.getSource(),StringArgumentType.getString(context,"destinationAddress"),""))
                            .then(Commands.argument("message",StringArgumentType.greedyString())
                                    .executes(context->sendDebugMessage(context.getSource(),StringArgumentType.getString(context,"destinationAddress"),StringArgumentType.getString(context,"message"))))));
        for(String name:new String[]{"oc_renderWirelessNetwork","oc_wlan"})
            event.getDispatcher().register(wirelessCommand(name));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> wirelessCommand(String name){
        return Commands.literal(name).requires(OriginalCommands::operator)
                .executes(context->wireless(context.getSource(),!wirelessRendering))
                .then(Commands.argument("enabled",StringArgumentType.word())
                        .suggests((context,builder)->SharedSuggestionProvider.suggest(new String[]{"true","false","1","0"},builder))
                        .executes(context->{String value=StringArgumentType.getString(context,"enabled");return wireless(context.getSource(),switch(value){case "true","1"->true;case "false","0"->false;default->throw failure("Invalid boolean: "+value);});}));
    }

    private static ServerPlayer player(CommandSourceStack source)throws CommandSyntaxException{
        if(source.getEntity() instanceof ServerPlayer player)return player;
        throw failure("Can only be used by players.");
    }

    private static int debugNanomachines(CommandSourceStack source)throws CommandSyntaxException{
        ServerPlayer player=player(source);
        if(Nanomachines.installController(player) instanceof ControllerImpl controller){
            controller.debug();
            NanomachineNetwork.sendConfiguration(player);
            player.sendSystemMessage(Component.literal("Debug configuration created, see log for mappings."));
            return 1;
        }
        return 0;
    }

    private static int printNanomachines(CommandSourceStack source,String name)throws CommandSyntaxException{
        ServerPlayer player=name==null?(source.getEntity() instanceof ServerPlayer self?self:null):source.getServer().getPlayerList().getPlayerByName(name);
        if(player==null)throw failure("Player entity not found.");
        if(Nanomachines.installController(player) instanceof ControllerImpl controller){
            controller.print();
            return 1;
        }
        return 0;
    }

    private static int sendDebugMessage(CommandSourceStack source,String destination,String message){
        Object[] data=message.isEmpty()?new Object[0]:message.split(" +");
        var endpoint=DebugNetwork.getEndpoint(destination);
        endpoint.ifPresent(target->target.receivePacket(Network.newPacket(source.getTextName(),destination,0,data)));
        return endpoint.isPresent()?1:0;
    }

    private static int wireless(CommandSourceStack source,boolean enabled){
        if(!source.getServer().isSingleplayer()){
            source.sendFailure(Component.literal("Wireless network debug rendering is only available in an integrated singleplayer server."));
            return 0;
        }
        wirelessRendering=enabled;
        wirelessBounds=enabled?WirelessNetwork.debugBounds():Map.of();
        return 1;
    }

    private static void tick(ServerTickEvent.Post event){
        if(wirelessRendering)wirelessBounds=WirelessNetwork.debugBounds();
    }

    private static void stopped(ServerStoppedEvent event){wirelessRendering=false;wirelessBounds=Map.of();}
    public static boolean wirelessRendering(){return wirelessRendering;}
    /** Immutable server-thread snapshot; the render thread never touches a live network tree. */
    public static List<WirelessNetwork.DebugBound> wirelessBounds(ResourceKey<Level> dimension){return wirelessBounds.getOrDefault(dimension,List.of());}
    private static CommandSyntaxException failure(String message){return new SimpleCommandExceptionType(Component.literal(message)).create();}
}
