package li.cil.oc.server.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import li.cil.oc.OpenComputers;
import li.cil.oc.api.Network;
import li.cil.oc.common.block.CaseBlock;
import li.cil.oc.common.block.ScreenBlock;
import li.cil.oc.common.block.entity.CaseBlockEntity;
import li.cil.oc.common.init.Blocks;
import li.cil.oc.common.init.Items;
import li.cil.oc.util.InventoryUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Original operator commands, registered through the server's Brigadier dispatcher. */
@EventBusSubscriber(modid=OpenComputers.ID)
public final class CommandHandler {
    private CommandHandler() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        for(String name:new String[]{"oc_spawnComputer","oc_sc"})
            event.getDispatcher().register(Commands.literal(name).requires(li.cil.oc.common.command.OriginalCommands::operator)
                    .executes(context->spawnComputer(context.getSource())));
        for(String name:new String[]{"oc_preventDisassembling","oc_nodis","oc_prevdis"})
            event.getDispatcher().register(disassemblyCommand(name));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> disassemblyCommand(String name) {
        return Commands.literal(name).requires(li.cil.oc.common.command.OriginalCommands::operator)
                .executes(context->preventDisassembly(context.getSource(),null))
                .then(Commands.argument("prevent",BoolArgumentType.bool())
                        .executes(context->preventDisassembly(context.getSource(),BoolArgumentType.getBool(context,"prevent"))));
    }

    private static int preventDisassembly(CommandSourceStack source,Boolean value)throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        ItemStack stack=player.getMainHandItem();
        if(stack.isEmpty())return 0;
        boolean prevent=value!=null?value:!stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean("oc:undisassemblable");
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->{
            if(prevent)tag.putBoolean("oc:undisassemblable",true);else tag.remove("oc:undisassemblable");
        });
        if(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).isEmpty())stack.remove(DataComponents.CUSTOM_DATA);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return 1;
    }

    private static int spawnComputer(CommandSourceStack source)throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        var level=player.serverLevel();
        var start=player.getEyePosition();
        var hit=level.clip(new ClipContext(start,start.add(player.getLookAngle().scale(16)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.BLOCK){source.sendFailure(Component.literal("You need to be looking at a nearby block."));return 0;}
        BlockPos computerPos=hit.getBlockPos().relative(hit.getDirection()),screenPos=computerPos.above(),keyboardPos=screenPos.above();
        for(BlockPos pos:new BlockPos[]{computerPos,screenPos,keyboardPos})
            if(!level.isInWorldBounds(pos)||!level.isEmptyBlock(pos)){
                source.sendFailure(Component.literal("Target position obstructed."));return 0;
            }
        Direction facing=player.getDirection().getOpposite();
        level.setBlockAndUpdate(computerPos,Blocks.CASES.get(3).get().defaultBlockState().setValue(CaseBlock.FACING,facing));
        level.setBlockAndUpdate(screenPos,Blocks.SCREENS.get(1).get().defaultBlockState()
                .setValue(ScreenBlock.PITCH,Direction.NORTH).setValue(ScreenBlock.YAW,facing));
        level.setBlockAndUpdate(keyboardPos,Blocks.KEYBOARD.get().defaultBlockState()
                .setValue(ScreenBlock.PITCH,Direction.UP).setValue(ScreenBlock.YAW,player.getDirection()));
        if(!(level.getBlockEntity(computerPos) instanceof CaseBlockEntity computer))return 0;
        Network.joinOrCreateNetwork(computer);
        insert(computer,"apucreative",1);
        insert(computer,"ram6",2);
        insert(computer,"hdd3",1);
        insert(computer,"luabios",1);
        insert(computer,"openos",1);
        return 1;
    }

    private static void insert(CaseBlockEntity computer,String name,int count) {
        var descriptor=Items.INSTANCE.get(name);
        if(descriptor==null)throw new IllegalStateException("Missing implemented computer hardware: "+name);
        ItemStack stack=descriptor.createItemStack(count);
        if(name.equals("apucreative"))li.cil.oc.server.machine.luac.LuaStateFactory.setDefaultArch(stack);
        InventoryUtils.insertIntoInventory(stack,InventoryUtils.asItemHandler(computer));
        if(!stack.isEmpty())throw new IllegalStateException("Computer rejected required hardware: "+name);
    }
}
