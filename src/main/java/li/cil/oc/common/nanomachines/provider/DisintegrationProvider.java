package li.cil.oc.common.nanomachines.provider;

import li.cil.oc.Settings;
import li.cil.oc.api.nanomachines.*;
import li.cil.oc.api.prefab.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import java.util.*;

public final class DisintegrationProvider extends AbstractProvider {
    public static final DisintegrationProvider INSTANCE=new DisintegrationProvider();
    private DisintegrationProvider(){super("c4e7e3c2-8069-4fbb-b08e-74b1bddcdfe7");}
    public Iterable<Behavior> createBehaviors(Player player){return List.of(new DisintegrationBehavior(player));}
    protected Behavior readBehaviorFromNBT(Player player,CompoundTag nbt){return new DisintegrationBehavior(player);}
    public static final class DisintegrationBehavior extends AbstractBehavior {
        private Map<BlockPos,SlowBreakInfo> breaking=new LinkedHashMap<>();
        DisintegrationBehavior(Player player){super(player);}
        public void onDisable(DisableReason reason){for(BlockPos pos:breaking.keySet())player.level().destroyBlockProgress(pos.hashCode(),pos,-1);breaking.clear();}
        public void update(){
            if(!(player instanceof ServerPlayer server)||player instanceof FakePlayer)return;Level level=player.level();Controller controller=li.cil.oc.api.Nanomachines.getController(player);if(controller==null)return;long now=level.getGameTime();Map<BlockPos,SlowBreakInfo> next=new LinkedHashMap<>();int range=Settings.get().nanomachineDisintegrationRange*controller.getInputCount(this);BlockPos origin=player.blockPosition();
            for(int x=-range;x<=range;x++)for(int y=0;y<=range*2;y++)for(int z=-range;z<=range;z++){
                BlockPos pos=origin.offset(x,y,z);if(!level.hasChunkAt(pos))continue;SlowBreakInfo existing=breaking.get(pos);
                if(existing!=null){if(existing.checkTool(player)){next.put(pos,existing);existing.update(level,now);}continue;}
                var event=new PlayerInteractEvent.LeftClickBlock(player,pos,player.getDirection(),PlayerInteractEvent.LeftClickBlock.Action.START);NeoForge.EVENT_BUS.post(event);boolean allowed=!event.isCanceled()&&event.getUseBlock()!=TriState.FALSE&&event.getUseItem()!=TriState.FALSE;
                if(!allowed||player.blockActionRestricted(level,pos,server.gameMode.getGameModeForPlayer())||level.isEmptyBlock(pos))continue;
                BlockState state=level.getBlockState(pos);float hardness=state.getDestroyProgress(player,level,pos);if(hardness>0){int duration=(int)(1/hardness);if(duration<20*30){next.put(pos,new SlowBreakInfo(now,now+duration,pos,player.getMainHandItem().copy(),state));level.destroyBlockProgress(pos.hashCode(),pos,0);}}
            }
            for(var entry:breaking.entrySet())if(entry.getValue().end<now){next.remove(entry.getKey());entry.getValue().finish(server);}
            for(BlockPos pos:breaking.keySet())if(!next.containsKey(pos))level.destroyBlockProgress(pos.hashCode(),pos,-1);breaking=next;
        }
    }
    private static final class SlowBreakInfo {
        final long start,end;final BlockPos pos;final ItemStack tool;final BlockState state;int damage;
        SlowBreakInfo(long start,long end,BlockPos pos,ItemStack tool,BlockState state){this.start=start;this.end=end;this.pos=pos;this.tool=tool;this.state=state;}
        boolean checkTool(Player player){ItemStack current=player.getMainHandItem();return tool.isEmpty()?current.isEmpty():!current.isEmpty()&&ItemStack.isSameItem(tool,current)&&(current.isDamageableItem()||current.getDamageValue()==tool.getDamageValue());}
        void update(Level level,long now){if(end>start){int next=(int)(10*(now-start)/(end-start));if(next!=damage){damage=next;level.destroyBlockProgress(pos.hashCode(),pos,damage);}}}
        void finish(ServerPlayer player){if(player.level().getBlockState(pos)==state){player.level().destroyBlockProgress(pos.hashCode(),pos,-1);if(player.gameMode.destroyBlock(pos))player.level().levelEvent(2001,pos,Block.getId(state));}}
    }
}
