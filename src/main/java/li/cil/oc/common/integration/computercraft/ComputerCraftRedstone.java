package li.cil.oc.common.integration.computercraft;

import dan200.computercraft.api.ComputerCraftAPI;
import li.cil.oc.common.redstone.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;

/** CC's sixteen digital wires share the existing OC sixteen-channel signal state. */
final class ComputerCraftRedstone implements BundledRedstone.Provider {
    @Override public int computeInput(Level level,BlockPos position,Direction side){return 0;}
    @Override public int[] computeBundledInput(Level level,BlockPos position,Direction side){
        int mask=ComputerCraftAPI.getBundledRedstoneOutput(level,position.relative(side),side.getOpposite());if(mask<0)return null;
        int[] values=new int[16];for(int color=0;color<16;color++)values[color]=(mask&(1<<color))==0?0:255;return values;
    }
    static int output(Level level,BlockPos position,Direction side){
        if(level==null||side==null||!level.hasChunkAt(position)||!(level.getBlockEntity(position) instanceof RedstoneHost host))return -1;
        if(!host.redstoneState().enabled())return 0;int mask=0;int[] values=host.redstoneState().bundledOutput(side);for(int color=0;color<16;color++)if(values[color]>0)mask|=1<<color;return mask;
    }
}
