package li.cil.oc.client;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.common.block.CaseBlock;
import li.cil.oc.common.block.entity.CaseBlockEntity;
import li.cil.oc.common.block.entity.RobotBlockEntity;
import li.cil.oc.common.init.Agents;
import li.cil.oc.server.agent.RobotController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client mirrors own these loops; no server machine state is queried by the sound engine. */
@EventBusSubscriber(modid = OpenComputers.ID, value = Dist.CLIENT)
public final class ComputerSounds {
    private static final SoundEvent RUNNING = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "computer_running"));
    private static final Map<Object, RunningSound> SOUNDS = new WeakHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private ComputerSounds() {}

    /** Internal identity check for the native engine's targeted lifecycle cleanup. */
    public static boolean isOwned(SoundInstance sound) { return sound instanceof RunningSound; }

    public static void caseChanged(CaseBlockEntity computer, boolean initial) {
        setRunning(computer, computer.getLevel(), computer.getBlockState().getValue(CaseBlock.RUNNING), initial);
    }

    public static void caseRemoved(CaseBlockEntity computer) {
        remove(computer);
    }

    public static void robotChanged(RobotController incoming, RobotController stable, boolean running, boolean initial) {
        if (incoming != stable) {
            RunningSound displaced = SOUNDS.remove(incoming);
            if (displaced != null) {
                if (SOUNDS.containsKey(stable)) displaced.finish();
                else {
                    displaced.owner = new WeakReference<>(stable);
                    SOUNDS.put(stable, displaced);
                }
            }
        }
        setRunning(stable, stable.proxy.getLevel(), running, initial);
    }

    public static void robotRemoved(RobotBlockEntity proxy, boolean chunkUnloaded) {
        RobotController robot = proxy.controller();
        if (robot.proxy != proxy) return;
        RunningSound sound = SOUNDS.get(robot);
        if (sound == null) return;
        // The next proxy's update packet can follow removal of the old proxy. Its
        // real afterimage keeps the existing voice alive until that packet arrives.
        if (!chunkUnloaded && proxy.getLevel().getBlockState(proxy.getBlockPos()).is(Agents.AFTERIMAGE.get())) {
            sound.movingFrom = proxy.getBlockPos();
        } else remove(robot);
    }

    private static void setRunning(Object owner, Level level, boolean running, boolean initial) {
        if (!running || Settings.get().soundVolume <= 0) {
            remove(owner);
            return;
        }
        RunningSound sound = SOUNDS.get(owner);
        if (sound == null) SOUNDS.put(owner, new RunningSound(owner, level, initial ? 1000 + RANDOM.nextInt(2000) : 50 + RANDOM.nextInt(50)));
        else sound.refresh();
    }

    private static void remove(Object owner) {
        RunningSound sound = SOUNDS.remove(owner);
        if (sound != null) sound.finish();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        float master = client.options.getSoundSourceVolume(SoundSource.MASTER);
        float blocks = client.options.getSoundSourceVolume(SoundSource.BLOCKS);
        Iterator<RunningSound> iterator = SOUNDS.values().iterator();
        while (iterator.hasNext()) {
            RunningSound sound = iterator.next();
            if (!sound.refresh()) {
                sound.finish();
                iterator.remove();
                continue;
            }
            if (blocks <= 0 && sound.played) sound.resumeAfterMute = true;
            // Vanilla removes the muted channel without removing soundDeleteTime;
            // isActive can therefore remain true after that channel is gone.
            if (sound.resumeAfterMute && master > 0 && blocks > 0) {
                sound.played = false;
                sound.resumeAfterMute = false;
            }
            if (!sound.played && !client.isPaused() && master > 0 && blocks > 0 && System.nanoTime() >= sound.startAt) {
                sound.played = true;
                client.getSoundManager().play(sound);
            }
        }
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        SOUNDS.values().forEach(RunningSound::finish);
        SOUNDS.clear();
    }

    @EventBusSubscriber(modid = OpenComputers.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() {}
        @SubscribeEvent public static void loaded(SoundEngineLoadEvent event) {
            Minecraft.getInstance().execute(() -> SOUNDS.values().forEach(sound -> sound.played = false));
        }
    }

    private static final class RunningSound extends AbstractTickableSoundInstance {
        WeakReference<Object> owner;
        final Level level;
        final long startAt;
        boolean played, resumeAfterMute;
        BlockPos movingFrom;

        RunningSound(Object owner, Level level, int delayMillis) {
            super(RUNNING, SoundSource.BLOCKS, RANDOM);
            this.owner = new WeakReference<>(owner);
            this.level = level;
            this.startAt = System.nanoTime() + delayMillis * 1_000_000L;
            looping = true;
            pitch = 1;
            refresh();
        }

        boolean refresh() {
            Object host = owner.get();
            Minecraft client = Minecraft.getInstance();
            if (host == null || level != client.level) return false;
            BlockPos position;
            if (host instanceof CaseBlockEntity computer) {
                if (computer.isRemoved() || !computer.getBlockState().getValue(CaseBlock.RUNNING)) return false;
                position = computer.getBlockPos();
            } else {
                RobotBlockEntity proxy = ((RobotController) host).proxy;
                if (proxy.isRemoved()) {
                    if (movingFrom == null || !level.getBlockState(movingFrom).is(Agents.AFTERIMAGE.get())) return false;
                    position = movingFrom;
                } else {
                    if (!proxy.clientRunning) return false;
                    position = proxy.getBlockPos();
                    movingFrom = null;
                }
            }
            x = position.getX();
            y = position.getY();
            z = position.getZ();
            volume = client.isPaused() ? 0 : 0.5f * Settings.get().soundVolume;
            return true;
        }

        @Override public boolean canStartSilent() { return true; }
        @Override public boolean canPlaySound() { return !isStopped(); }
        @Override public void tick() { if (!refresh()) finish(); }

        void finish() {
            stop();
            Minecraft.getInstance().getSoundManager().stop(this);
        }
    }
}
