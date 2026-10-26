package li.cil.oc.client;

import li.cil.oc.api.Manual;
import li.cil.oc.common.item.ManualItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/** Client-only connection from the usable manual item to the public manual facade. */
public final class ManualItemClient {
    private ManualItemClient() {}

    public static void initialize() {
        ManualItem.setClientActions(new ManualItem.ClientActions() {
            @Override public void open(Player player, boolean reset) {
                if (reset) Manual.reset();
                Manual.openFor(player);
            }

            @Override public boolean openForBlock(Player player, BlockPos position) {
                String path = Manual.pathFor(player.level(), position);
                if (path == null) return false;
                Manual.openFor(player);
                Manual.reset();
                Manual.navigate(path);
                return true;
            }
        });
    }
}
