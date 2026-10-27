package li.cil.oc.common;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.network.EnvironmentHost;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** The original host-local debounce and SoundEffect range/volume conversion. */
public final class DiskSounds {
    private static final Map<EnvironmentHost, Map<String, Long>> TIMEOUTS = new WeakHashMap<>();
    private DiskSounds() {}

    public static synchronized void play(EnvironmentHost host, String name) {
        if (!(host.world() instanceof ServerLevel level) || Settings.get().soundVolume <= 0) return;
        long now = System.currentTimeMillis();
        Map<String, Long> timeouts = TIMEOUTS.computeIfAbsent(host, ignored -> new HashMap<>());
        if (timeouts.getOrDefault(name, 0L) > now) return;
        double range = 15 * Settings.get().soundVolume;
        var sound = Holder.direct(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, name)));
        var packet = new ClientboundSoundPacket(sound, SoundSource.BLOCKS, host.xPosition(), host.yPosition(), host.zPosition(), (float) (range / 15 + 0.5), 1, level.random.nextLong());
        level.getServer().getPlayerList().broadcast(null, host.xPosition(), host.yPosition(), host.zPosition(), range, level.dimension(), packet);
        timeouts.put(name, now + 500);
    }
}
