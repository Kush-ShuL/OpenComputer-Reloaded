package ocfixture;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real whitelist commands, real bound cards, actual players and owned world cells. */
public final class DebugCardContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-debug-card");
    private static Run active;
    private DebugCardContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){Run run=new Run(level,position.immutable(),results);active=run;try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}}
    public static boolean isRunning(){return active!=null;}
    public static void tick(){Run run=active;if(run==null)return;try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}active=null;run.cleanup();}
    public static void close(){Run run=active;active=null;if(run!=null)run.cleanup();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object first(Object[] result,String operation){require(result!=null&&result.length>0,operation+" returned no value");return result[0];}
    private static String text(Object value){return value instanceof byte[] bytes?new String(bytes,java.nio.charset.StandardCharsets.UTF_8):String.valueOf(value);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing debug fixture item "+name);return descriptor.createItemStack(1);}
    @FunctionalInterface private interface Operation {void run()throws Exception;}
    private static void rejected(Operation operation,String message)throws Exception{try{operation.run();}catch(Exception error){require(error.getMessage()!=null&&error.getMessage().contains(message),"Unexpected debug rejection: "+error);return;}throw new AssertionError("Actual debug operation did not reject "+message);}
    private static void near(Object value,double expected,String operation){require(value instanceof Number number&&Math.abs(number.doubleValue()-expected)<.000001,operation+": expected "+expected+", actual "+value);}
    private record DebugMessage(Node source,Object[] data){}
    private static final class Observer extends AbstractManagedEnvironment {
        final List<DebugMessage> messages=new ArrayList<>();
        Observer(){setNode(Network.newNode(this,Visibility.Network).withComponent("debug_contract_observer",Visibility.Network).create());Network.joinNewNetwork(node());}
        @Override public void onMessage(Message message){if("computer.signal".equals(message.name())&&message.data().length>0&&"debug_message".equals(text(message.data()[0])))messages.add(new DebugMessage(message.source(),message.data().clone()));}
    }
    private static final class BreakGuard {
        final ServerLevel level;final BlockPos position;int calls;
        BreakGuard(ServerLevel level,BlockPos position){this.level=level;this.position=position;}
        @SubscribeEvent public void breaking(BlockEvent.BreakEvent event){if(event.getLevel()==level&&event.getPos().equals(position)){calls++;event.setCanceled(true);}}
    }
    private static final class Run {
        final ServerLevel level;final BlockPos position,otherPos,screenPos,blockPos,fillPos,scanPos,waterPos,lightPos,playerPos,commandPos;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();final List<Entity> entities=new ArrayList<>();
        final EmbeddedChannel channel=new EmbeddedChannel();final ConcurrentLinkedQueue<Packet<?>> packets=new ConcurrentLinkedQueue<>();
        final String suffix=UUID.randomUUID().toString().substring(0,6),team="ocd_t_"+suffix,objective="ocd_o_"+suffix;
        Case computer,other;ServerPlayer actor;Component card,second;Observer observer;BreakGuard breakGuard;Value world,player,scoreboard,persistedWorld;
        boolean joined,whitelisted,operator,worldSaved,cleaned;long oldTime,startGameTime;BlockPos oldSpawn;float oldSpawnAngle;boolean oldRain,oldThunder;int oldRainTime,oldThunderTime,oldClear,ticks,phase;
        Run(ServerLevel level,BlockPos position,List<String> results){
            this.level=level;this.position=position;this.results=results;otherPos=position.west(4);screenPos=position.south(6);blockPos=position.south(2);fillPos=position.east(2).south(2);scanPos=position.north(3);waterPos=position.west(4).south(3);lightPos=position.north(6).above(2);playerPos=position.east(6).above(2);commandPos=playerPos.below();
        }
        void remember(BlockPos pos){
            if(restored.containsKey(pos))return;if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Debug fixture cell is occupied: "+pos);restored.put(pos.immutable(),level.getBlockState(pos));
        }
        BlockEntity place(BlockPos pos,BlockState state){remember(pos);require(level.setBlock(pos,state,3),"Cannot place actual debug fixture cell "+pos);return level.getBlockEntity(pos);}
        Case placeCase(BlockPos pos){BlockEntity entity=place(pos,li.cil.oc.api.Items.get("case3").block().defaultBlockState());require(entity instanceof Case,"Actual debug host is not a Case");return (Case)entity;}
        void start()throws Exception{
            require(API.config!=null&&"whitelist".equals(API.config.getString("debug.debugCardAccess")),"Debug contract requires the isolated whitelist configuration");
            require(!level.getServer().isSingleplayer(),"The permission branch requires the isolated dedicated server");
            computer=placeCase(position);other=placeCase(otherPos);place(screenPos,li.cil.oc.api.Items.get("screen1").block().defaultBlockState());
            for(BlockPos pos:List.of(blockPos,fillPos,fillPos.east(),scanPos,lightPos,playerPos,commandPos,position.below()))remember(pos);
            remember(waterPos);for(Direction side:List.of(Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST))place(waterPos.relative(side),Blocks.BARRIER.defaultBlockState());
            ServerLevelData data=(ServerLevelData)level.getLevelData();oldTime=level.getDayTime();startGameTime=level.getGameTime();oldSpawn=level.getSharedSpawnPos();oldSpawnAngle=level.getSharedSpawnAngle();oldRain=data.isRaining();oldThunder=data.isThundering();oldRainTime=data.getRainTime();oldThunderTime=data.getThunderTime();oldClear=data.getClearWeatherTime();worldSaved=true;
            actor=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"OC_Debug_"+suffix),ClientInformation.createDefault());
            Connection transport=new Connection(PacketFlow.SERVERBOUND){
                @Override public Channel channel(){return Run.this.channel;}
                @Override public boolean isConnected(){return Run.this.channel.isOpen();}
                @Override public SocketAddress getRemoteAddress(){return new InetSocketAddress("127.0.0.1",0);}
                @Override public void send(Packet<?> packet,PacketSendListener listener,boolean flush){packets.add(packet);}
            };
            channel.pipeline().addLast("packet_handler",transport);transport.channelActive(channel.pipeline().context(transport));NetworkRegistry.configureMockConnection(transport);
            actor.connection=new ServerGamePacketListenerImpl(level.getServer(),transport,actor,CommonListenerCookie.createInitial(actor.getGameProfile(),false));
            level.getServer().getPlayerList().placeNewPlayer(transport,actor,CommonListenerCookie.createInitial(actor.getGameProfile(),false));joined=true;
            require(level.getServer().getPlayerList().getPlayer(actor.getUUID())==actor,"Public login did not register the actual debug player");
            actor.setNoGravity(true);actor.teleportTo(playerPos.getX()+.5,playerPos.getY(),playerPos.getZ()+.5);
        }
        int console(String command)throws Exception{return level.getServer().getCommands().getDispatcher().execute(command,level.getServer().createCommandSourceStack().withSuppressedOutput());}
        Component installed(Case host){for(Node node:host.node().network().nodes())if(node instanceof Component component&&"debug".equals(component.name())&&host.componentSlot(node.address())==0)return component;throw new AssertionError("Actual Case did not mount its debug card");}
        void insert(Case host,ItemStack stack){require(host.canPlaceItem(0,stack),"Actual Case rejects the debug card");host.setItem(0,stack);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);}
        InteractionResult use(ItemStack stack){actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setShiftKeyDown(true);try{return stack.getItem().use(level,actor,InteractionHand.MAIN_HAND).getResult();}finally{actor.setShiftKeyDown(false);}}
        void binding()throws Exception{
            ItemStack stack=item("debugcard");require(use(stack)==InteractionResult.FAIL,"Non-whitelisted actual player was able to bind a debug card");
            require(!Driver.driverFor(stack).dataTag(stack).contains("oc:player"),"Rejected real binding changed the card owner");
            require(console("oc_debugWhitelist add "+actor.getGameProfile().getName())==1,"Actual console did not whitelist the owned test account");whitelisted=true;
            require(use(stack)==InteractionResult.SUCCESS,"Actual whitelisted player could not bind the real card");
            CompoundTag data=Driver.driverFor(stack).dataTag(stack);require(actor.getGameProfile().getName().equals(data.getString("oc:player"))&&!data.getString("oc:accessNonce").isEmpty(),"Actual binding did not persist its real player/nonce");
            require(stack.getMaxStackSize()==64&&stack.getRarity()==Rarity.COMMON&&!stack.getItem().isBookEnchantable(stack,new ItemStack(Items.ENCHANTED_BOOK)),"Unaddressed real binding changed the original stack/rarity/book-enchantment rules");
            insert(computer,stack);card=installed(computer);require(computer.getItem(0).getMaxStackSize()==1,"Addressed actual debug card is not limited to one item");
            ItemStack unbound=item("debugcard");insert(other,unbound);Component unboundCard=installed(other);rejected(()->unboundCard.invoke("getX",other.machine()),"Shift+Click");
            unbound=other.removeItem(0,1);require(use(unbound)==InteractionResult.SUCCESS,"Actual second card could not bind through item use");insert(other,unbound);second=installed(other);
            near(first(card.invoke("getX",computer.machine()),"getX"),position.getX()+.5,"Actual debug X");near(first(card.invoke("getY",computer.machine()),"getY"),position.getY()+.5,"Actual debug Y");near(first(card.invoke("getZ",computer.machine()),"getZ"),position.getZ()+.5,"Actual debug Z");
            results.add("PASS debug.binding actual whitelist command and player item-use reject unauthorized/unbound cards, persist real bindings, retain original stack/rarity/enchantment rules and enable actual Case-position callbacks");
        }
        Value value(Object[] result,String operation){Object value=first(result,operation);require(value instanceof Value,operation+" did not return actual userdata");return (Value)value;}
        Object[] call(Value value,String method,Object... args)throws Exception{return computer.machine().invoke(value,method,args);}
        void set(BlockPos pos,String block)throws Exception{call(world,"setBlock",pos.getX(),pos.getY(),pos.getZ(),block,0);}
        void objects()throws Exception{
            world=value(card.invoke("getWorld",computer.machine()),"getWorld");player=value(card.invoke("getPlayer",computer.machine(),actor.getGameProfile().getName()),"getPlayer");scoreboard=value(card.invoke("getScoreboard",computer.machine()),"getScoreboard");
            near(first(call(world,"getDimensionId"),"getDimensionId"),0,"Original overworld dimension ID");require(level.dimension().location().getPath().equals(text(first(call(world,"getDimensionName"),"getDimensionName"))),"Actual debug dimension name differs from the original name");
            Value indexed=value(card.invoke("getWorld",computer.machine(),0),"getWorld(id)");near(first(call(indexed,"getSeed"),"getSeed"),level.getSeed(),"Actual indexed world seed");
            CompoundTag saved=new CompoundTag();world.save(saved);persistedWorld=(Value)world.getClass().getConstructor().newInstance();persistedWorld.load(saved);near(first(call(persistedWorld,"getDimensionId"),"restored world"),0,"Restored actual world value");
            Object names=first(card.invoke("getPlayers",computer.machine()),"getPlayers");require(names instanceof Object[] array&&Arrays.stream(array).anyMatch(name->actor.getGameProfile().getName().equals(text(name))),"Actual online debug player is missing from the component list");
            Value offline=value(card.invoke("getPlayer",computer.machine(),"OC_Offline_"+suffix),"offline getPlayer");Object[] absent=call(offline,"getHealth");require(absent.length==2&&absent[0]==null&&"player is offline".equals(text(absent[1])),"Actual offline player lookup changed its original result");
            GameType previous=actor.gameMode.getGameModeForPlayer();call(player,"setGameType","creative");require(actor.isCreative()&&"creative".equals(text(first(call(player,"getGameType"),"getGameType"))),"Actual player mode callback did not change the online player");call(player,"setGameType",previous.getName());
            float health=actor.getHealth();call(player,"setHealth",13.5);near(actor.getHealth(),13.5,"Actual player health mutation");near(first(call(player,"getHealth"),"getHealth"),13.5,"Actual player health callback");near(first(call(player,"getMaxHealth"),"getMaxHealth"),actor.getMaxHealth(),"Actual player maximum health");call(player,"setHealth",health);
            int initialLevel=actor.experienceLevel;call(player,"addExperienceLevel",3);call(player,"removeExperienceLevel",1);near(first(call(player,"getLevel"),"getLevel"),initialLevel+2,"Actual player experience levels");near(first(call(player,"getExperienceTotal"),"getExperienceTotal"),actor.totalExperience,"Actual total experience readback");
            call(player,"insertItem","minecraft:diamond",4,0,"");require(count(actor.getInventory(),Items.DIAMOND)==4,"Actual debug player insertion did not reach the real inventory");call(player,"clearInventory");require(actor.getInventory().isEmpty(),"Actual debug player clearInventory left items");
            call(player,"setPosition",playerPos.getX()+.5,playerPos.getY(),playerPos.getZ()+.5);Object[] coordinates=call(player,"getPosition");near(coordinates[0],actor.getX(),"Actual player X readback");near(coordinates[1],actor.getY(),"Actual player Y readback");near(coordinates[2],actor.getZ(),"Actual player Z readback");
            Value playerWorld=value(call(player,"getWorld"),"player.getWorld");near(first(call(playerWorld,"getDimensionId"),"player world"),0,"Actual player world");
            results.add("PASS debug.values real and publicly serialized world userdata retain dimension/access, real online/offline player values mutate only the owned player's mode/health/experience/inventory/position through actual Case Context callbacks");
        }
        int count(Container container,net.minecraft.world.item.Item item){int count=0;for(int slot=0;slot<container.getContainerSize();slot++)if(container.getItem(slot).is(item))count+=container.getItem(slot).getCount();return count;}
        @SuppressWarnings("unchecked") void blocks()throws Exception{
            set(blockPos,"minecraft:oak_log");near(first(call(world,"getMetadata",blockPos.getX(),blockPos.getY(),blockPos.getZ()),"getMetadata"),0,"Default actual state index");
            call(world,"setBlock",blockPos.getX(),blockPos.getY(),blockPos.getZ(),"minecraft:oak_log",1);near(first(call(world,"getMetadata",blockPos.getX(),blockPos.getY(),blockPos.getZ()),"nondefault metadata"),1,"Actual state-index round trip");
            near(first(call(world,"getBlockId",blockPos.getX(),blockPos.getY(),blockPos.getZ()),"getBlockId"),BuiltInRegistries.BLOCK.getId(Blocks.OAK_LOG),"Actual block ID");
            rejected(()->call(world,"getBlockState",blockPos.getX(),blockPos.getY(),blockPos.getZ(),"bad"),"boolean");
            require(Boolean.TRUE.equals(first(call(world,"isLoaded",blockPos.getX(),blockPos.getY(),blockPos.getZ()),"isLoaded"))&&Boolean.FALSE.equals(first(call(world,"isLoaded",blockPos.getX(),level.getMaxBuildHeight(),blockPos.getZ()),"isLoaded(height)")),"Actual loaded query did not respect modern world height");
            call(world,"setBlocks",fillPos.east().getX(),fillPos.getY(),fillPos.getZ(),fillPos.getX(),fillPos.getY(),fillPos.getZ(),"minecraft:stone",0);require(level.getBlockState(fillPos).is(Blocks.STONE)&&level.getBlockState(fillPos.east()).is(Blocks.STONE),"Actual reversed-corner setBlocks did not fill its two owned cells");
            set(blockPos,"minecraft:chest");require(Boolean.TRUE.equals(first(call(world,"hasTileEntity",blockPos.getX(),blockPos.getY(),blockPos.getZ()),"hasTileEntity")),"Actual created chest has no tile entity through the callback");
            Map<?,?> typed=(Map<?,?>)first(call(world,"getTileNBT",blockPos.getX(),blockPos.getY(),blockPos.getZ()),"getTileNBT");Map<Object,Object> compound=new LinkedHashMap<>((Map<Object,Object>)typed.get("value"));compound.put("CustomName",Map.of("type",8,"value","{\"text\":\"Actual debug chest\"}"));
            require(Boolean.TRUE.equals(first(call(world,"setTileNBT",blockPos.getX(),blockPos.getY(),blockPos.getZ(),Map.of("type",10,"value",compound)),"setTileNBT"))&&level.getBlockEntity(blockPos) instanceof Nameable named&&"Actual debug chest".equals(named.getName().getString()),"Actual typed-NBT callback did not update the real chest name");
            require(Boolean.TRUE.equals(first(call(world,"insertItem","minecraft:apple",7,0,"",blockPos.getX()+.25,blockPos.getY()+.25,blockPos.getZ()+.25,Direction.UP.get3DDataValue()),"world.insertItem")),"Actual debug inventory insertion failed");
            Container chest=(Container)level.getBlockEntity(blockPos);require(count(chest,Items.APPLE)==7,"Actual chest contents differ from debug insertion");near(first(call(world,"removeItem",blockPos.getX(),blockPos.getY(),blockPos.getZ(),1,3),"world.removeItem"),3,"Actual debug extraction");require(count(chest,Items.APPLE)==4,"Actual debug extraction changed the wrong amount");
            rejected(()->call(world,"insertItem","minecraft:apple",1,0,"",blockPos.getX(),blockPos.getY(),blockPos.getZ(),6),"invalid side");
            Object[] noTank=call(world,"insertFluid","minecraft:water",1000,waterPos.getX(),waterPos.getY(),waterPos.getZ(),1);require(noTank.length==2&&noTank[0]==null&&"no tank".equals(text(noTank[1]))&&level.isEmptyBlock(waterPos),"Actual non-tank fluid insertion changed its original result or placed world liquid: "+Arrays.toString(noTank));
            noTank=call(world,"removeFluid",1000,waterPos.getX(),waterPos.getY(),waterPos.getZ(),1);require(noTank.length==2&&noTank[0]==null&&"no tank".equals(text(noTank[1])),"Actual non-tank fluid removal changed its original result: "+Arrays.toString(noTank));
            results.add("PASS debug.world real block/state-index and bounded-area operations, height checks, argument errors, registry-aware typed chest NBT and actual inventory counts use only owned cells; non-tank fluid operations return nil/no tank without placing world liquid");
        }
        void scanning()throws Exception{
            ItemEntity nearer=new ItemEntity(level,scanPos.getX()+.5,scanPos.getY()+.5,scanPos.getZ()+.5,new ItemStack(Items.STICK));nearer.setNoGravity(true);nearer.setPickUpDelay(Integer.MAX_VALUE);require(level.addFreshEntity(nearer),"Cannot add the actual nearest item entity");entities.add(nearer);
            ArmorStand living=new ArmorStand(level,scanPos.getX()+.85,scanPos.getY()+.5,scanPos.getZ()+.5);living.setNoGravity(true);require(level.addFreshEntity(living),"Cannot add actual scan living entity");entities.add(living);
            Object[] contents=card.invoke("scanContentsAt",computer.machine(),scanPos.getX(),scanPos.getY(),scanPos.getZ());require(Boolean.FALSE.equals(contents[0])&&"air".equals(text(contents[1])),"Original nearest non-living entity was incorrectly filtered out before scanning the block");
            nearer.discard();contents=card.invoke("scanContentsAt",computer.machine(),scanPos.getX(),scanPos.getY(),scanPos.getZ());require(Boolean.TRUE.equals(contents[0])&&"EntityLivingBase".equals(text(contents[1])),"Actual living entity was not reported after removing the nearer item");living.discard();
            set(waterPos,"minecraft:water");breakGuard=new BreakGuard(level,waterPos);NeoForge.EVENT_BUS.register(breakGuard);
            contents=card.invoke("scanContentsAt",computer.machine(),waterPos.getX(),waterPos.getY(),waterPos.getZ());require(Boolean.TRUE.equals(contents[0])&&"liquid".equals(text(contents[1]))&&breakGuard.calls==1&&level.getBlockState(waterPos).is(Blocks.WATER),"Actual protected liquid scan did not preserve its original break-event result without destroying water");NeoForge.EVENT_BUS.unregister(breakGuard);breakGuard=null;
            results.add("PASS debug.scan actual nearest item/living entity ordering matches the original, and scanning owned contained water observes a real canceled BlockEvent without breaking it");
        }
        void scoreboard()throws Exception{
            String name=actor.getGameProfile().getName();call(scoreboard,"addTeam",team);require(Boolean.TRUE.equals(first(call(scoreboard,"addPlayerToTeam",name,team),"addPlayerToTeam"))&&level.getScoreboard().getPlayerTeam(team).getPlayers().contains(name),"Actual scoreboard team membership did not change");
            call(scoreboard,"addObjective",objective,"dummy");call(scoreboard,"setPlayerScore",name,objective,42);call(scoreboard,"increasePlayerScore",name,objective,2);call(scoreboard,"decreasePlayerScore",name,objective,5);
            near(first(call(scoreboard,"getPlayerScore",name,objective),"getPlayerScore"),39,"Actual debug score");require(level.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(name),level.getScoreboard().getObjective(objective)).value()==39,"Actual scoreboard storage differs from the returned debug score");
            require(Boolean.TRUE.equals(first(call(scoreboard,"removePlayerFromTeams",name),"removePlayerFromTeams")),"Actual scoreboard did not remove its owned member");
            results.add("PASS debug.scoreboard actual owned team/objective membership and score mutation/readback agree with the real server scoreboard");
        }
        void commands()throws Exception{
            String command="setblock ~ ~-1 ~ minecraft:gold_block";Object[] denied=card.invoke("runCommand",computer.machine(),command);near(denied[0],0,"Ordinary bound-player command permission");require(level.isEmptyBlock(commandPos),"Unprivileged debug command changed its owned target cell");
            level.getServer().getPlayerList().op(actor.getGameProfile());operator=true;
            Object[] result=card.invoke("runCommand",computer.machine(),command);near(result[0],1,"Privileged actual debug command result");require(level.getBlockState(commandPos).is(Blocks.GOLD_BLOCK)&&level.isEmptyBlock(position.below()),"Actual debug command used the Case position instead of the original bound-player position");
            require(result.length==2&&result[1]!=null,"Actual successful command did not capture feedback");
            results.add("PASS debug.command actual bound non-operator cannot run setblock; temporary real operator authorization enables the original command at that player's coordinates and captures real feedback");
        }
        Node remote(){BlockEntity entity=level.getBlockEntity(screenPos);require(entity instanceof SidedEnvironment,"Actual remote screen has no sided SDK entry");for(Direction side:Direction.values()){Node node=((SidedEnvironment)entity).sidedNode(side);if(node!=null)return node;}throw new AssertionError("Actual remote screen exposes no node");}
        void network()throws Exception{
            Node remote=remote();require(Boolean.TRUE.equals(first(card.invoke("connectToBlock",computer.machine(),screenPos.getX(),screenPos.getY(),screenPos.getZ()),"connectToBlock"))&&card.isNeighborOf(remote),"Actual debug remote connection did not join its owned screen");
            observer=new Observer();observer.node().connect(second);byte[] bytes={0,127,(byte)128,(byte)255};card.invoke("sendToDebugCard",computer.machine(),second.address(),"actual-debug-payload",bytes);
            require(observer.messages.size()==1,"Actual cross-card debug message was not delivered exactly once");DebugMessage message=observer.messages.getFirst();require(message.source==second&&message.data.length==6&&"debug_message".equals(text(message.data[0]))&&card.address().equals(text(message.data[1]))&&((Number)message.data[2]).intValue()==0&&((Number)message.data[3]).doubleValue()==0&&"actual-debug-payload".equals(text(message.data[4]))&&message.data[5] instanceof byte[] data&&Arrays.equals(data,bytes),"Actual debug message changed its source/port/distance/payload");
            Component previous=card;String address=card.address();ItemStack removed=computer.removeItem(0,1);require(previous.network()==null,"Actual removed debug card retained a network");insert(computer,removed);card=installed(computer);require(card!=previous&&address.equals(card.address())&&card.isNeighborOf(remote()),"Actual debug-card remount lost its address or delayed remote-screen connection");
            String secondAddress=second.address();other.removeItem(0,1);int delivered=observer.messages.size();card.invoke("sendToDebugCard",computer.machine(),secondAddress,"removed");require(second.network()==null&&observer.messages.size()==delivered,"Detached actual debug card remained registered for messages");
            results.add("PASS debug.network real debug cards exchange original message fields; actual card item removal/remount preserves identity and reconnects its remote screen, while removing the recipient unregisters its endpoint");
        }
        void worldFlags()throws Exception{
            ServerLevelData data=(ServerLevelData)level.getLevelData();int rain=data.getRainTime(),thunder=data.getThunderTime(),clear=data.getClearWeatherTime();boolean raining=data.isRaining(),thundering=data.isThundering();
            call(world,"setRaining",!raining);call(world,"setThundering",!thundering);require(data.isRaining()!=raining&&data.isThundering()!=thundering&&data.getRainTime()==rain&&data.getThunderTime()==thunder&&data.getClearWeatherTime()==clear,"Actual debug weather switches reset original timers");data.setRaining(raining);data.setThundering(thundering);
            call(world,"setSpawnPoint",fillPos.getX(),fillPos.getY(),fillPos.getZ());Object[] spawn=call(world,"getSpawnPoint");near(spawn[0],fillPos.getX(),"Actual spawn X");near(spawn[1],fillPos.getY(),"Actual spawn Y");near(spawn[2],fillPos.getZ(),"Actual spawn Z");level.setDefaultSpawnPos(oldSpawn,oldSpawnAngle);
            call(world,"setTime",18000);near(first(call(world,"getTime"),"getTime"),18000,"Actual world time mutation");
        }
        void revoke()throws Exception{
            require(console("oc_debugWhitelist revoke "+actor.getGameProfile().getName())==1,"Actual console failed to revoke the owned account's cards");
            rejected(()->card.invoke("getX",computer.machine()),"invalidated");rejected(()->call(world,"getTime"),"invalidated");rejected(()->call(persistedWorld,"getDimensionId"),"invalidated");rejected(()->call(player,"getHealth"),"invalidated");rejected(()->call(scoreboard,"getPlayerScore",actor.getGameProfile().getName(),objective),"invalidated");
            String address=card.address();ItemStack stack=computer.removeItem(0,1);require(use(stack)==InteractionResult.SUCCESS&&!Driver.driverFor(stack).dataTag(stack).contains("oc:player"),"Actual owner could not unbind the revoked card");require(use(stack)==InteractionResult.SUCCESS,"Actual owner could not bind the card to the fresh whitelist nonce");insert(computer,stack);card=installed(computer);near(first(card.invoke("getX",computer.machine()),"rebound getX"),position.getX()+.5,"Actual rebound card position");require(address.equals(card.address()),"Actual nonce rebinding changed the retained node address");
            results.add("PASS debug.revoke actual nonce revocation rejects the mounted card and all already-issued/restored player/world/scoreboard values; real unbind/rebind restores access while keeping card identity");
        }
        boolean tick()throws Exception{
            require(++ticks<=100,"Debug contract exceeded 100 actual ticks; phase="+phase);require(!computer.machine().isRunning()&&!other.machine().isRunning(),"Debug fixture must not boot firmware");
            if(phase==0){if(ticks<3)return false;binding();objects();blocks();scanning();scoreboard();commands();network();worldFlags();phase=1;return false;}
            near(first(call(world,"getLightValue",lightPos.getX(),lightPos.getY(),lightPos.getZ()),"getLightValue"),level.getMaxLocalRawBrightness(lightPos,0),"Original actual light lookup without night sky subtraction");
            revoke();return true;
        }
        void fail(Throwable error){results.add("FAIL debug.card phase="+phase+": "+error);LOGGER.error("DEBUG_CARD failed at phase {}",phase,error);}
        void cleanup(){
            if(cleaned)return;cleaned=true;if(breakGuard!=null)NeoForge.EVENT_BUS.unregister(breakGuard);for(Entity entity:entities)entity.discard();entities.clear();if(observer!=null)observer.node().remove();
            if(computer!=null){computer.clearContent();computer.machine().stop();}if(other!=null){other.clearContent();other.machine().stop();}
            if(level.getScoreboard().getObjective(objective)!=null)level.getScoreboard().removeObjective(level.getScoreboard().getObjective(objective));if(level.getScoreboard().getPlayerTeam(team)!=null)level.getScoreboard().removePlayerTeam(level.getScoreboard().getPlayerTeam(team));
            if(actor!=null){actor.stopUsingItem();actor.getInventory().clearContent();if(operator)level.getServer().getPlayerList().deop(actor.getGameProfile());if(whitelisted)try{console("oc_debugWhitelist remove "+actor.getGameProfile().getName());}catch(Exception error){LOGGER.error("Could not remove the isolated debug whitelist entry",error);}if(joined)level.getServer().getPlayerList().remove(actor);}
            if(worldSaved){ServerLevelData data=(ServerLevelData)level.getLevelData();data.setRaining(oldRain);data.setThundering(oldThunder);data.setRainTime(oldRainTime);data.setThunderTime(oldThunderTime);data.setClearWeatherTime(oldClear);level.setDayTime(oldTime+level.getGameTime()-startGameTime);level.setDefaultSpawnPos(oldSpawn,oldSpawnAngle);}
            var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof Container container)container.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}restored.clear();channel.finishAndReleaseAll();packets.clear();for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);forced.clear();
        }
    }
}
