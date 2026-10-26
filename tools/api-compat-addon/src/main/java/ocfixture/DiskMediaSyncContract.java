package ocfixture;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Exercises NeoForge's actual update-packet receiver on a detached same-type block entity. */
public final class DiskMediaSyncContract {
    private DiskMediaSyncContract(){}
    public static void verify(BlockEntity source,HolderLookup.Provider registries,List<String> results){
        require(source instanceof Container inventory&&!inventory.isEmpty(),"Media sync requires the actual populated external drive");
        Container inventory=(Container)source;BlockEntity copy=source.getType().create(source.getBlockPos(),source.getBlockState());require(copy instanceof Container,"Drive type did not create a detached inventory receiver");
        Connection incoming=new Connection(PacketFlow.CLIENTBOUND);ClientboundBlockEntityDataPacket populated=(ClientboundBlockEntityDataPacket)source.getUpdatePacket();require(populated!=null,"Populated drive did not create its update packet");copy.onDataPacket(incoming,populated,registries);require(!((Container)copy).isEmpty(),"Real onDataPacket did not load the inserted medium");
        ItemStack removed=inventory.removeItem(0,1);
        try{require(!removed.isEmpty()&&inventory.isEmpty(),"Server-side removal did not empty the real drive");ClientboundBlockEntityDataPacket empty=(ClientboundBlockEntityDataPacket)source.getUpdatePacket();require(empty!=null,"Empty drive did not create its update packet");copy.onDataPacket(incoming,empty,registries);require(((Container)copy).isEmpty(),"NeoForge onDataPacket retained a ghost medium after receiving the actual empty-drive update");}
        finally{inventory.setItem(0,removed);}
        results.add("PASS drive.media_sync actual filled then empty update packets reach NeoForge onDataPacket and clear the detached same-type inventory used by rendering");
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
