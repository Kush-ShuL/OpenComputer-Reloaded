package li.cil.oc.server.driver.block;

import li.cil.oc.api.driver.*;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Shared matching only; vanilla drivers retain their own original callback surfaces. */
abstract class VanillaBlockDriver<T extends BlockEntity> implements DriverBlock,EnvironmentProvider {
    private final Class<T> type;private final Block[] blocks;
    VanillaBlockDriver(Class<T> type,Block... blocks){this.type=type;this.blocks=blocks;}
    @Override public final boolean worksWith(Level level,BlockPos pos,Direction side){return li.cil.oc.common.integration.VanillaDrivers.integrationEnabled("minecraft")&&level.hasChunkAt(pos)&&type.isInstance(level.getBlockEntity(pos));}
    @Override public final ManagedEnvironment createEnvironment(Level level,BlockPos pos,Direction side){return !level.isClientSide&&worksWith(level,pos,side)?create(level,pos,side,type.cast(level.getBlockEntity(pos))):null;}
    protected abstract ManagedEnvironment create(Level level,BlockPos pos,Direction side,T tile);
    protected abstract Class<?> environmentType();
    @Override public final Class<?> getEnvironment(ItemStack stack){if(stack.getItem() instanceof BlockItem item)for(Block block:blocks)if(item.getBlock()==block)return environmentType();return null;}
}
