package li.cil.oc.mixin.printing;

import li.cil.oc.common.block.entity.PrintBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replaces the removed Forge per-instance beacon-base hook only inside the base scan. */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconPrintMixin {
    @Redirect(method = "updateBase", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState opencomputers$printedBeaconBase(Level level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        return state.getBlock() instanceof li.cil.oc.common.block.PrintBlock
                && level.getBlockEntity(position) instanceof PrintBlockEntity print && print.data.isBeaconBase
                ? Blocks.IRON_BLOCK.defaultBlockState() : state;
    }
}
