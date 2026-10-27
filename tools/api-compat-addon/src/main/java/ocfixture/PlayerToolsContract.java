package ocfixture;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.API;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Actual item interactions with a real ServerPlayer and a local public mock transport. */
public final class PlayerToolsContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-player-tools");
    private static Run active;
    private PlayerToolsContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        Run run=new Run(level,position.immutable(),results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}
        active=null;run.cleanup();
    }
    public static void close(){Run run=active;active=null;if(run!=null)run.cleanup();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing player tool "+name);return descriptor.createItemStack(1);}

    private static final class Run {
        final ServerLevel level;final BlockPos position,casePos,ordinaryPos,colorPos,replayPos;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final EmbeddedChannel channel=new EmbeddedChannel();
        ServerPlayer actor;Case computer;BlockEntity charger;ItemStack debugger;int ticks,phase,useTicks;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;casePos=position.east(4);ordinaryPos=casePos.east(2);colorPos=position.south(3);replayPos=colorPos.east(2);}
        void remember(BlockPos pos){
            if(restored.containsKey(pos))return;
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));
            level.getChunkAt(pos);require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Player-tool fixture cell is occupied: "+pos);restored.put(pos.immutable(),level.getBlockState(pos));
        }
        BlockEntity place(BlockPos pos,BlockState state){remember(pos);require(level.setBlock(pos,state,3),"Cannot place tool fixture block "+pos);return level.getBlockEntity(pos);}
        BlockState block(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null&&descriptor.block()!=null,"Missing tool fixture block "+name);return descriptor.block().defaultBlockState();}
        void start(){
            charger=place(position,block("charger"));BlockEntity host=place(casePos,block("case3"));require(host instanceof Case,"Debugger target is not an actual Case");computer=(Case)host;
            place(ordinaryPos,Blocks.STONE.defaultBlockState());remember(colorPos);remember(replayPos);
            actor=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"OC_ToolProof"),ClientInformation.createDefault());
            Connection transport=new Connection(PacketFlow.SERVERBOUND){
                @Override public Channel channel(){return Run.this.channel;}
                @Override public boolean isConnected(){return Run.this.channel.isOpen();}
                @Override public void send(Packet<?> packet,PacketSendListener listener,boolean flush){}
            };
            NetworkRegistry.configureMockConnection(transport);
            actor.connection=new ServerGamePacketListenerImpl(level.getServer(),transport,actor,CommonListenerCookie.createInitial(actor.getGameProfile(),false));
            actor.setNoGravity(true);actor.setPos(position.getX()+.5,position.getY()+1,position.getZ()-2);debugger=item("debugger");
        }
        BlockHitResult hit(BlockPos pos,Direction side){return new BlockHitResult(Vec3.atCenterOf(pos),side,pos,false);}
        InteractionResult firstUse(ItemStack stack,BlockPos pos,Direction side){
            actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);
            return stack.getItem().onItemUseFirst(stack,new UseOnContext(actor,InteractionHand.MAIN_HAND,hit(pos,side)));
        }
        boolean inverted(){return charger.getUpdateTag(level.registryAccess()).getBoolean("oc:invertSignal");}
        void wrench(){
            ItemStack wrench=item("wrench"),before=wrench.copy();BlockState initial=level.getBlockState(position);Direction facing=initial.getValue(BlockStateProperties.HORIZONTAL_FACING);boolean inversion=inverted();
            require(firstUse(wrench,position,Direction.UP)==InteractionResult.CONSUME,"Charger top face did not consume the actual wrench item-first rotation");
            require(level.getBlockState(position).getValue(BlockStateProperties.HORIZONTAL_FACING)==facing.getClockWise()&&inverted()==inversion,"Charger top wrench interaction did not rotate clockwise without inversion");
            require(firstUse(wrench,position,Direction.DOWN)==InteractionResult.CONSUME,"Charger bottom face did not consume the actual wrench item-first rotation");
            require(level.getBlockState(position).equals(initial)&&inverted()==inversion,"Charger bottom wrench rotation did not restore its facing without inversion");
            require(firstUse(wrench,position,Direction.NORTH)==InteractionResult.PASS,"Charger side wrench interaction did not fall through to its normal block interaction");
            ItemInteractionResult used=level.getBlockState(position).useItemOn(wrench,level,actor,InteractionHand.MAIN_HAND,hit(position,Direction.NORTH));
            require(used==ItemInteractionResult.sidedSuccess(false)&&level.getBlockState(position).equals(initial)&&inverted()!=inversion,"Actual charger side block interaction did not invert without rotation");
            require(level.getBlockEntity(position)==charger&&ItemStack.matches(before,wrench),"Actual wrench interaction replaced the charger or consumed/damaged the tool");
            results.add("PASS tools.wrench real item-first charger top/bottom rotations restore its facing; a side PASS reaches the actual block inversion with the same undamaged wrench and block entity");
        }
        Set<Node> graph(){Set<Node> nodes=new LinkedHashSet<>();computer.node().network().nodes().forEach(nodes::add);return nodes;}
        void debugger(){
            require(!level.getServer().getPlayerList().isOp(actor.getGameProfile()),"Debugger actor must be an ordinary non-operator ServerPlayer");
            Set<Node> baseline=graph();Direction side=computer.facing().getOpposite();
            require(firstUse(debugger,casePos,side)==InteractionResult.SUCCESS,"Real ordinary ServerPlayer could not use the debugger on a Case");
            Set<Node> added=graph();added.removeAll(baseline);require(added.size()==1,"Actual debugger did not add exactly one monitor node to the real Case graph");
            Node monitor=added.iterator().next();String address=monitor.address();
            require(address!=null&&monitor.isNeighborOf(computer.node()),"Actual debugger monitor is not directly connected to the selected Case node");
            require(firstUse(debugger,ordinaryPos,Direction.NORTH)==InteractionResult.SUCCESS,"Debugger refused an actual ordinary stone block");
            require(monitor.network()==null&&graph().equals(baseline),"Using the debugger on a non-environment block did not detach the actual monitor");
            require(firstUse(debugger,casePos,side)==InteractionResult.SUCCESS,"Debugger could not reconnect to the actual Case");
            added=graph();added.removeAll(baseline);
            require(added.size()==1&&added.contains(monitor)&&address.equals(monitor.address())&&monitor.isNeighborOf(computer.node()),"Debugger reconnect replaced its real node identity or address");
            require(firstUse(debugger,ordinaryPos,Direction.NORTH)==InteractionResult.SUCCESS&&monitor.network()==null,"Actual debugger final disconnection failed");
            results.add("PASS tools.debugger a real non-operator ServerPlayer connects the actual monitor to a Case, detaches on stone and reconnects the same node/address; transport is local and no remote client is claimed");
        }
        void placeItem(BlockPos pos,ItemStack stack){
            actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);
            require(stack.getItem() instanceof BlockItem&&((BlockItem)stack.getItem()).place(new BlockPlaceContext(actor,InteractionHand.MAIN_HAND,stack,hit(pos,Direction.UP))).consumesAction(),"Actual colored BlockItem placement failed");
        }
        void chameliumColor(){
            ItemStack colored=item("chameliumblock");colored.set(DataComponents.BLOCK_STATE,new BlockItemStateProperties(Map.of("color","orange")));placeItem(colorPos,colored);
            BlockState state=level.getBlockState(colorPos);ItemStack clone=state.getBlock().getCloneItemStack(level,colorPos,state);
            require(clone.is(item("chameliumblock").getItem())&&"orange".equals(clone.getOrDefault(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).properties().get("color")),"Actual non-black Chamelium clone lost its color");
            List<ItemStack> drops=Block.getDrops(state,level,colorPos,null,actor,ItemStack.EMPTY);
            require(drops.size()==1&&ItemStack.matches(clone,drops.getFirst()),"Bare-hand Chamelium drops did not retain the same non-black color as clone");
            placeItem(replayPos,drops.getFirst().copy());require(level.getBlockState(replayPos).equals(state),"Actual Chamelium drop BlockItem did not replay its retained color");
            results.add("PASS tools.chamelium.color actual orange BlockItem placement, bare-hand Block.getDrops and clone preserve color; the real dropped item replays the same block state");
        }
        void ink(){
            ItemStack full=item("inkcartridge"),before=full.copy(),empty=full.getCraftingRemainingItem();
            require(full.hasCraftingRemainingItem()&&empty.is(item("inkcartridgeempty").getItem())&&empty.getCount()==1&&ItemStack.matches(before,full),"Actual full cartridge crafting remainder is not one empty cartridge without changing the input");
            require(!empty.hasCraftingRemainingItem()&&empty.getCraftingRemainingItem().isEmpty(),"Actual empty cartridge creates another crafting remainder");
            results.add("PASS tools.ink the actual standard ItemStack crafting-remainder API returns one empty cartridge for full ink and no further remainder for the empty cartridge");
        }
        boolean startChamelium(){
            ItemStack material=item("chamelium");material.setCount(2);actor.setItemInHand(InteractionHand.MAIN_HAND,material);
            require(material.getUseAnimation()==UseAnim.EAT&&material.getUseDuration(actor)==32,"Actual Chamelium use animation/duration differs from 32-tick eating");
            boolean edible=API.config.getBoolean("printer.chameliumEdible");
            require(material.getItem().use(level,actor,InteractionHand.MAIN_HAND).getResult()==InteractionResult.SUCCESS&&actor.isUsingItem()==edible&&material.getCount()==2,"Actual Chamelium start-use does not obey the configured edible flag");
            if(!edible)results.add("PASS tools.chamelium.use configured inedible material returns SUCCESS without starting use or consuming an item");
            useTicks=0;return edible;
        }
        void startAcid(){
            actor.removeAllEffects();ItemStack acid=item("acid");actor.setItemInHand(InteractionHand.MAIN_HAND,acid);
            require(acid.getUseAnimation()==UseAnim.DRINK&&acid.getUseDuration(actor)==32&&acid.getItem().use(level,actor,InteractionHand.MAIN_HAND).getResult()==InteractionResult.SUCCESS&&actor.isUsingItem(),"Actual acid did not start its normal 32-tick drink use");useTicks=0;
        }
        boolean advanceUse(){actor.doTick();require(++useTicks<=32,"Actual material use exceeded its 32-tick duration");if(actor.isUsingItem())return false;require(useTicks==32,"Actual material completed before 32 player lifecycle ticks");return true;}
        boolean tick(){
            require(++ticks<=80,"Player-tool contract exceeded 80 actual world ticks; phase="+phase);
            require(!computer.machine().isRunning(),"Player-tool fixture must not boot firmware");
            if(phase==0){if(ticks<3)return false;wrench();debugger();chameliumColor();ink();if(startChamelium())phase=1;else{startAcid();phase=2;}return false;}
            if(!advanceUse())return false;
            if(phase==1){
                require(actor.getMainHandItem().is(item("chamelium").getItem())&&actor.getMainHandItem().getCount()==1&&actor.hasEffect(MobEffects.INVISIBILITY)&&actor.hasEffect(MobEffects.BLINDNESS),"Actual Chamelium completion did not consume one material and apply both original effects");
                results.add("PASS tools.chamelium.use configured edible material completes through 32 public ServerPlayer.doTick lifecycle calls, consumes one of two and applies invisibility/blindness");
                startAcid();phase=2;return false;
            }
            require(actor.getMainHandItem().isEmpty()&&actor.hasEffect(MobEffects.BLINDNESS)&&actor.hasEffect(MobEffects.POISON)&&actor.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&actor.hasEffect(MobEffects.CONFUSION)&&actor.hasEffect(MobEffects.SATURATION),"Actual acid completion did not consume its single item without a container and apply its five original effects");
            results.add("PASS tools.acid actual 32-tick public player-use lifecycle consumes the last acid with no bottle/container remainder and applies all five original effects");return true;
        }
        void fail(Throwable error){results.add("FAIL player.tools phase="+phase+": "+error);LOGGER.error("PLAYER_TOOLS failed at phase {}",phase,error);}
        void cleanup(){
            if(actor!=null){if(debugger!=null&&level.getBlockState(ordinaryPos).is(Blocks.STONE))firstUse(debugger,ordinaryPos,Direction.NORTH);actor.stopUsingItem();actor.removeAllEffects();actor.getInventory().clearContent();}
            if(computer!=null)computer.machine().stop();
            var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries)level.setBlock(entry.getKey(),entry.getValue(),3);restored.clear();
            channel.finishAndReleaseAll();for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);forced.clear();
        }
    }
}
