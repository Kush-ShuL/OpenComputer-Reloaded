package li.cil.oc.mixin.client;

import com.google.common.collect.Multimap;
import com.mojang.blaze3d.audio.Channel;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import li.cil.oc.client.ComputerSounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's zero-category-volume branch leaves native indexes behind for tickable sounds. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow @Final private Multimap<SoundSource, SoundInstance> instanceBySource;
    @Shadow @Final private List<TickableSoundInstance> tickingSounds;
    @Shadow @Final private List<TickableSoundInstance> queuedTickableSounds;
    @Shadow @Final private Map<SoundInstance, Integer> queuedSounds;
    @Shadow @Final private Map<SoundInstance, Integer> soundDeleteTime;

    @Inject(method = "tick", at = @At("TAIL"))
    private void opencomputers$releaseRunningSounds(boolean paused, CallbackInfo callback) {
        // All vanilla container iterations have finished. Only our private Loop
        // instances are touched, including when the integrated world is paused.
        instanceToChannel.entrySet().removeIf(entry -> {
            if (!ComputerSounds.isOwned(entry.getKey()) || !((TickableSoundInstance) entry.getKey()).isStopped()) return false;
            entry.getValue().execute(Channel::stop);
            return true;
        });
        Set<SoundInstance> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        tickingSounds.removeIf(sound -> ComputerSounds.isOwned(sound) && (opencomputers$discarded(sound) || !seen.add(sound)));
        queuedTickableSounds.removeIf(this::opencomputers$discarded);
        soundDeleteTime.keySet().removeIf(this::opencomputers$discarded);
        queuedSounds.keySet().removeIf(this::opencomputers$discarded);
        instanceBySource.values().removeIf(this::opencomputers$discarded);
    }

    @Unique private boolean opencomputers$discarded(SoundInstance sound) {
        return ComputerSounds.isOwned(sound) && (((TickableSoundInstance) sound).isStopped() || !instanceToChannel.containsKey(sound));
    }
}
