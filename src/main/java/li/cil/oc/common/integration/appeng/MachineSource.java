package li.cil.oc.common.integration.appeng;

import java.util.Optional;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import net.minecraft.world.entity.player.Player;

/** Preserve AE's machine action attribution; never impersonate a player. */
final class MachineSource implements IActionSource {
    private final IActionHost host;
    MachineSource(IActionHost host) { this.host = host; }
    @Override public Optional<Player> player() { return Optional.empty(); }
    @Override public Optional<IActionHost> machine() { return Optional.of(host); }
    @Override public <T> Optional<T> context(Class<T> type) { return Optional.empty(); }
}
