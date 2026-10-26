package li.cil.oc.server.component;

import com.mojang.brigadier.arguments.StringArgumentType;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.common.item.data.DebugCardData.AccessContext;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

/** Original debug-card policy and revocable, case-insensitive whitelist bindings. */
public final class DebugAccess {
    private static final Map<String,String> NONCES=new TreeMap<>();private static boolean loaded;
    private static final SecureRandom RANDOM=new SecureRandom();
    private DebugAccess(){}
    private static String mode(){Object value=Settings.get().config.getValue("debug.debugCardAccess").unwrapped();String mode=String.valueOf(value).toLowerCase(Locale.ROOT);return switch(mode){case "true","allow"->"allow";case "whitelist"->"whitelist";default->"deny";};}
    private static Path file(){return FMLPaths.CONFIGDIR.get().resolve("opencomputers/debug_card_whitelist.txt");}
    private static synchronized void load(){if(loaded)return;loaded=true;if(!Files.isRegularFile(file()))return;try{for(String line:Files.readAllLines(file(),StandardCharsets.UTF_8)){String[] parts=line.split(" ",2);if(parts.length==2)NONCES.put(parts[0].toLowerCase(Locale.ROOT),parts[1]);}}catch(java.io.IOException error){throw new IllegalStateException("Cannot load debug card whitelist",error);}}
    private static synchronized void save(){try{Files.createDirectories(file().getParent());List<String> lines=new ArrayList<>();NONCES.forEach((name,nonce)->lines.add(name+" "+nonce));Files.write(file(),lines,StandardCharsets.UTF_8);}catch(java.io.IOException error){throw new IllegalStateException("Cannot save debug card whitelist",error);}}
    private static String fresh(){byte[] bytes=new byte[16];RANDOM.nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
    public static synchronized Optional<String> nonce(String player){load();return Optional.ofNullable(NONCES.get(player.toLowerCase(Locale.ROOT)));}
    public static boolean whitelisted(){return mode().equals("whitelist");}
    public static void check(Optional<AccessContext> access){switch(mode()){case "allow"->{}case "deny"->throw new IllegalStateException("debug card is disabled");default->{AccessContext context=access.orElseThrow(()->new IllegalStateException("debug card is whitelisted, Shift+Click with it to bind card to yourself"));String current=nonce(context.player()).orElseThrow(()->new IllegalStateException("you are not whitelisted to use debug card"));if(!current.equals(context.nonce()))throw new IllegalStateException("debug card is invalidated, please re-bind it to yourself");}}}
    private static int command(CommandSourceStack source,String action,String player){if(!whitelisted()){source.sendFailure(Component.literal("Debug card whitelisting is not enabled."));return 0;}load();String name=player.toLowerCase(Locale.ROOT);switch(action){case "list"->source.sendSuccess(()->Component.literal(NONCES.isEmpty()?"There are no currently whitelisted players.":"Currently whitelisted players: "+String.join(", ",NONCES.keySet())),false);case "add"->{if(!NONCES.containsKey(name)){NONCES.put(name,fresh());save();}source.sendSuccess(()->Component.literal("Player was added to whitelist."),false);}case "remove"->{if(NONCES.remove(name)!=null)save();source.sendSuccess(()->Component.literal("Player was removed from whitelist."),false);}case "revoke"->{if(!NONCES.containsKey(name)){source.sendFailure(Component.literal("You are not whitelisted to use debug card."));return 0;}NONCES.put(name,fresh());save();source.sendSuccess(()->Component.literal("All your debug cards were invalidated."),false);}default->throw new IllegalArgumentException("invalid whitelist operation");}return 1;}
    public static void commands(RegisterCommandsEvent event){var root=Commands.literal("oc_debugWhitelist").then(Commands.literal("revoke").executes(context->command(context.getSource(),"revoke",context.getSource().getTextName())).then(Commands.argument("player",StringArgumentType.word()).requires(source->source.hasPermission(2)).executes(context->command(context.getSource(),"revoke",StringArgumentType.getString(context,"player"))))).then(Commands.literal("list").requires(source->source.hasPermission(2)).executes(context->command(context.getSource(),"list","")));for(String action:new String[]{"add","remove"})root.then(Commands.literal(action).requires(source->source.hasPermission(2)).then(Commands.argument("player",StringArgumentType.word()).executes(context->command(context.getSource(),action,StringArgumentType.getString(context,"player")))));event.getDispatcher().register(root);}
}
