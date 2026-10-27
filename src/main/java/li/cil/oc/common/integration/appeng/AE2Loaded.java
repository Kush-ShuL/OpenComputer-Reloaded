package li.cil.oc.common.integration.appeng;

import appeng.api.movable.BlockEntityMoveStrategies;
import appeng.api.movable.DefaultBlockEntityMoveStrategy;
import li.cil.oc.api.Driver;
import li.cil.oc.common.init.Printing;
import li.cil.oc.common.integration.WrenchTools;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Loaded only after the optional mod and blacklist gate. */
final class AE2Loaded {
    private AE2Loaded() {}
    static void initialize() {
        for (var kind : AE2Drivers.Kind.values()) {
            var driver = new AE2Drivers(kind);
            Driver.add((li.cil.oc.api.driver.DriverBlock)driver);
            Driver.add((li.cil.oc.api.driver.EnvironmentProvider)driver);
        }
        Driver.add(new ConverterCellInventory());
        WrenchTools.addCheck(AE2Loaded::isWrench);
        WrenchTools.addUsage((player, position, changeDurability) -> isWrench(player.getMainHandItem()) && !player.isSpectator()
                && player.level().mayInteract(player, position)
                && player.mayUseItemAt(position, Direction.UP, player.getMainHandItem()));
        BlockEntityMoveStrategies.add(new DefaultBlockEntityMoveStrategy() {
            @Override public boolean canHandle(BlockEntityType<?> type) { return type == Printing.PRINT_TYPE.get(); }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> Craftable.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> Craftable.stop());
    }
    private static boolean isWrench(ItemStack stack) {
        return !stack.isEmpty() && (appeng.util.InteractionUtil.canWrenchRotate(stack) || appeng.util.InteractionUtil.canWrenchDisassemble(stack));
    }
}
