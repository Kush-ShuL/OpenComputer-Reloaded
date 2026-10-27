package ocfixture;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.API;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.DriverItem;
import li.cil.oc.api.internal.Adapter;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** An actual bound MFU joins a remote native screen at the configured range boundary, then survives removal and remount. */
public final class RemoteAdapterContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-remote-adapter-contract");
    private static Run active;
    private RemoteAdapterContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        require(active==null,"A remote adapter contract is already active");Run run=new Run(level,position.immutable(),results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}
        active=null;run.cleanup();
    }
    public static void close(){Run run=active;active=null;if(run!=null){run.results.add("FAIL remote.adapter: server stopped before the world contract completed");run.cleanup();}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static CompoundTag tag(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}

    private static final class Run {
        final ServerLevel level;final BlockPos position,screenPos;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        Case host;Adapter adapter;EnvironmentHost adapterHost;Environment screen;Node screenNode;FakePlayer player;
        DriverItem driver;int[] boundCoordinates;String boundDimension,upgradeAddress;int ticks,phase,changedAt;boolean cleaned;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;screenPos=position.east(3);this.results=results;}
        void remember(BlockPos pos){
            if(restored.containsKey(pos))return;
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Remote adapter fixture refuses an occupied cell "+pos);restored.put(pos.immutable(),level.getBlockState(pos));
        }
        void place(String name,BlockPos pos){
            remember(pos);var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null&&descriptor.block()!=null,"Missing real remote adapter fixture block "+name);
            require(level.setBlock(pos,descriptor.block().defaultBlockState(),3),"Could not place actual "+name);
        }
        void start(){
            require(API.config!=null&&API.config.getInt("misc.mfuRange")==3&&!API.config.getBoolean("power.ignorePower"),"MFU boundary contract requires normal range three and enabled power accounting");
            place("case3",position.west());place("adapter",position);remember(position.east());remember(position.east(2));place("screen1",screenPos);
            require(level.getBlockEntity(position.west()) instanceof Case,"Remote adapter requires an actual public Case host");host=(Case)level.getBlockEntity(position.west());
            require(level.getBlockEntity(position) instanceof Adapter&&level.getBlockEntity(position) instanceof EnvironmentHost,"Actual adapter exposes no SDK inventory/environment host");adapter=(Adapter)level.getBlockEntity(position);adapterHost=(EnvironmentHost)adapter;
            require(level.getBlockEntity(screenPos) instanceof Environment&&level.getBlockEntity(screenPos) instanceof SidedEnvironment,"Actual remote screen exposes no public native environment");screen=(Environment)level.getBlockEntity(screenPos);
            player=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes("oc-remote-adapter-bind-operator".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"OCRemoteBind"));
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);player.setPos(screenPos.getX()+.5,screenPos.getY(),screenPos.getZ()+2);
        }
        void connected(boolean expected,String message){
            require(host.machine().node().network()!=null&&adapter.node().network()==host.machine().node().network(),"Actual neighboring Case and Adapter did not naturally form their local network");
            require(screen.node()==screenNode&&screenNode.network()!=null,"MFU operation removed or replaced the real screen's own node");
            require((screenNode.network()==adapter.node().network())==expected,message);
        }
        void bind(){
            screenNode=screen.node();require(screenNode!=null&&((SidedEnvironment)screen).sidedNode(Direction.SOUTH)==screenNode,"Chosen native screen face is not connectable");connected(false,"Distant screen unexpectedly has a physical network path");
            require(level.isEmptyBlock(position.east())&&level.isEmptyBlock(position.east(2)),"The remote connection has a physical cable/block path instead of two air gaps");
            require(host.machine().node() instanceof Connector,"Actual Case machine has no shared energy connector");Connector energy=(Connector)host.machine().node();energy.changeBuffer(Double.POSITIVE_INFINITY);require(energy.globalBuffer()>0,"Could not charge the actual Case energy network");
            ItemStack stack=li.cil.oc.api.Items.get("mfu").createItemStack(1);player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.setShiftKeyDown(true);
            var hit=new BlockHitResult(Vec3.atCenterOf(screenPos).add(0,0,.5),Direction.SOUTH,screenPos,false);
            try{require(stack.getItem().onItemUseFirst(stack,new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Actual sneaking MFU item binding did not succeed");}
            finally{player.setShiftKeyDown(false);}
            CompoundTag root=tag(stack);int legacy=level.dimension().equals(Level.OVERWORLD)?0:level.dimension().equals(Level.NETHER)?-1:level.dimension().equals(Level.END)?1:Integer.MIN_VALUE;
            boundCoordinates=new int[]{screenPos.getX(),screenPos.getY(),screenPos.getZ(),legacy,Direction.SOUTH.get3DDataValue()};boundDimension=level.dimension().location().toString();
            require(Arrays.equals(root.getIntArray("oc:coord"),boundCoordinates)&&boundDimension.equals(root.getString("oc:dimension")),"Actual binding did not store the target coordinates, native dimension and chosen face");
            driver=Driver.driverFor(stack,adapterHost.getClass());require(driver!=null&&adapter.canPlaceItem(0,stack),"Actual Adapter rejected its legitimately bound MFU");adapter.setItem(0,stack);changedAt=ticks;
        }
        void persisted(ItemStack stack){
            CompoundTag root=tag(stack);require(Arrays.equals(root.getIntArray("oc:coord"),boundCoordinates)&&boundDimension.equals(root.getString("oc:dimension")),"MFU slot lifecycle lost the player's actual target binding");
            CompoundTag saved=driver.dataTag(stack);require(saved.contains("node",Tag.TAG_COMPOUND)&&saved.contains("oc:adapter.block",Tag.TAG_COMPOUND),"Actual MFU save omitted its original node or adapter-block record");
            String address=saved.getCompound("node").getString("address");require(!address.isEmpty(),"Mounted MFU did not preserve its legitimately joined network identity");
            if(upgradeAddress==null)upgradeAddress=address;else require(upgradeAddress.equals(address),"Removing/remounting the bound MFU changed its persisted node identity");
        }
        boolean tick(){
            require(++ticks<=100,"Remote adapter contract exceeded 100 actual world ticks");
            if(phase==0){if(ticks<3)return false;bind();phase=1;return false;}
            if(ticks-changedAt<2)return false;
            if(phase==1){
                connected(true,"Bound MFU did not connect its real native screen at exactly three-block center distance");persisted(adapter.getItem(0));
                ItemStack removed=adapter.removeItem(0,1);require(!removed.isEmpty(),"Could not remove the actual mounted MFU");persisted(removed);connected(false,"Removing the real MFU left the native screen joined to the adapter");
                results.add("PASS mfu.native actual sneak-bound item connects a native screen across two air cells at the configured three-block range boundary, disconnects on slot removal and preserves its node/binding/adapter-block save records");
                adapter.setItem(0,removed);changedAt=ticks;phase=2;return false;
            }
            connected(true,"Reinserting the actual saved MFU did not reconnect the same native screen");persisted(adapter.getItem(0));
            results.add("PASS mfu.remount actual saved MFU remount reconnects the same world screen and retains the original joined node identity and target binding without starting a VM");return true;
        }
        void fail(Throwable error){results.add("FAIL remote.adapter: "+error);LOGGER.error("REMOTE_ADAPTER_CONTRACT FAIL",error);}
        void cleanup(){
            if(cleaned)return;cleaned=true;if(adapter!=null)adapter.clearContent();if(host!=null)host.machine().stop();if(player!=null)player.getInventory().clearContent();
            var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries)level.setBlock(entry.getKey(),entry.getValue(),3);
            for(long packed:forced){ChunkPos chunk=new ChunkPos(packed);level.setChunkForced(chunk.x,chunk.z,false);}forced.clear();
        }
    }
}
