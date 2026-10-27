package ocfixture;

import com.mojang.blaze3d.audio.Channel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import li.cil.oc.api.API;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Node;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Observes native sound-engine instances driven by real Case/Robot state packets. */
@OnlyIn(Dist.CLIENT)
public final class ComputerSoundClientContract {
    private static final ResourceLocation RUNNING = ResourceLocation.fromNamespaceAndPath("opencomputers", "computer_running");
    private static final byte[] BIOS = "while true do computer.pullSignal(1) end".getBytes(StandardCharsets.UTF_8);
    private static volatile Run active;
    private static boolean registered;
    private ComputerSoundClientContract() {}

    public static void start(Minecraft client, Path directory, List<String> results) {
        require(active == null, "Computer sound contract is already running");
        require(client.player != null && client.level != null && client.hasSingleplayerServer(), "Sound contract requires its normally loaded owned integrated world");
        if (!registered) {
            registered = true;
            NeoForge.EVENT_BUS.addListener(ComputerSoundClientContract::played);
            NeoForge.EVENT_BUS.addListener(ComputerSoundClientContract::streamed);
            NeoForge.EVENT_BUS.addListener(ComputerSoundClientContract::serverTick);
        }
        active = new Run(client, results);
        try { active.start(); } catch (Throwable error) { active.fail(error); }
    }

    public static boolean isRunning() { return active != null; }

    public static void tick() {
        Run run = active;
        if (run == null) return;
        try { run.tick(); } catch (Throwable error) { run.fail(error); }
        if (run.finishing && run.cleanup != null && run.cleanup.isDone()) {
            try { run.cleanup.join(); } catch (Throwable error) { run.results.add("FAIL C22.sound cleanup: " + error); }
            active = null;
        }
    }

    private static void played(PlaySoundEvent event) {
        Run run = active;
        SoundInstance sound = event.getSound();
        if (run != null && !run.finishing && sound != null && RUNNING.equals(sound.getLocation())) run.observed.add(sound);
    }

    private static void streamed(PlayStreamingSourceEvent event) {
        Run run = active;
        if (run != null && !run.finishing && RUNNING.equals(event.getSound().getLocation())) run.channels.put(event.getSound(), event.getChannel());
    }

    private static void serverTick(ServerTickEvent.Post event) {
        Run run = active;
        if (run == null || run.finishing || event.getServer() != run.server) return;
        try { run.serverTick(); } catch (Throwable error) { run.serverError = error; }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static ItemStack item(String name) {
        var descriptor = li.cil.oc.api.Items.get(name);
        require(descriptor != null, "Missing real sound fixture hardware " + name);
        return descriptor.createItemStack(1);
    }

    private static final class Run {
        final Minecraft client;
        final MinecraftServer server;
        final ServerLevel level;
        final List<String> results;
        final BlockPos casePos, robotPos;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final Set<Long> forced = new LinkedHashSet<>();
        final List<SoundInstance> observed = new ArrayList<>();
        final Map<SoundInstance, Channel> channels = new ConcurrentHashMap<>();
        final float originalMaster, originalBlocks;
        final float expectedVolume;
        final long started = System.nanoTime();
        Case computer;
        Robot robot;
        Component control;
        BlockPos movedPos;
        Class<? extends Architecture> architecture;
        CompletableFuture<Void> placement, command, cleanup;
        volatile Throwable serverError;
        volatile boolean serverReady;
        boolean machinesStarted;
        volatile boolean finishing;
        int phase, robotStarts, caseStarts, muteTicks, muteCycles;
        SoundInstance caseVoice, robotVoice, stoppedCaseVoice;

        Run(Minecraft client, List<String> results) {
            this.client = client;
            this.server = client.getSingleplayerServer();
            this.level = server.getLevel(client.level.dimension());
            this.results = results;
            casePos = client.player.blockPosition().east(5).above(3);
            robotPos = casePos.south(5);
            originalMaster = client.options.getSoundSourceVolume(SoundSource.MASTER);
            originalBlocks = client.options.getSoundSourceVolume(SoundSource.BLOCKS);
            expectedVolume = 0.5f * Math.clamp((float) API.config.getDouble("client.soundVolume"), 0, 2);
        }

        void remember(BlockPos position) {
            if (restored.containsKey(position)) return;
            if (level.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true)) forced.add(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
            level.getChunkAt(position);
            require(level.isEmptyBlock(position) && level.getBlockEntity(position) == null, "Sound fixture refuses occupied cell " + position);
            restored.put(position, level.getBlockState(position));
        }

        void start() {
            require(expectedVolume > 0, "Sound proof requires enabled actual OC sound volume");
            require(client.screen == null, "Sound contract requires the ordinary game view");
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0);
            client.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).set(1.0);
            placement = CompletableFuture.runAsync(() -> {
                architecture = li.cil.oc.api.Machine.architectures().stream()
                    .filter(type -> "Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(type))).findFirst()
                    .orElseThrow(() -> new AssertionError("Native Lua 5.3 is not registered"));
                remember(casePos);
                require(level.setBlock(casePos, li.cil.oc.api.Items.get("case3").block().defaultBlockState(), 3), "Could not place real sound Case");
                require(level.getBlockEntity(casePos) instanceof Case, "Sound Case lacks its published SDK interface");
                computer = (Case) level.getBlockEntity(casePos);
                ItemStack cpu = item("cpu1");
                require(Driver.driverFor(cpu) instanceof MutableProcessor, "Actual CPU has no public architecture selector");
                ((MutableProcessor) Driver.driverFor(cpu)).setArchitecture(cpu, architecture);
                for (ItemStack part : List.of(cpu, item("ram1"), eeprom())) {
                    boolean inserted = false;
                    for (int slot = 0; slot < computer.getContainerSize(); slot++) if (computer.getItem(slot).isEmpty() && computer.canPlaceItem(slot, part)) {
                        computer.setItem(slot, part); inserted = true; break;
                    }
                    require(inserted, "Actual Case rejected sound fixture hardware " + part);
                }
                CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
                ItemStack preset = null;
                for (ItemStack stack : CreativeTab.instance.getDisplayItems()) if (stack.is(item("robot").getItem()) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("oc:tier") == 3) {
                    preset = stack.copy(); break;
                }
                require(preset != null && preset.getItem() instanceof BlockItem, "CreativeTab has no configured real Robot");
                CompoundTag data = preset.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                ListTag parts = new ListTag();
                int cpus = 0, flashes = 0;
                for (Tag raw : data.getList("oc:components", Tag.TAG_COMPOUND)) {
                    ItemStack part = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) raw);
                    var driver = Driver.driverFor(part);
                    if (driver instanceof MutableProcessor processor) { processor.setArchitecture(part, architecture); cpus++; }
                    if (driver != null && "eeprom".equals(driver.slot(part))) { part = eeprom(); flashes++; }
                    parts.add(part.saveOptional(level.registryAccess()));
                }
                require(cpus == 1 && flashes == 1, "Sound preset must retain one real CPU and EEPROM");
                data.put("oc:components", parts);
                preset.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                remember(robotPos);
                for (Direction side : Direction.values()) remember(robotPos.relative(side));
                var actor = server.getPlayerList().getPlayer(client.player.getUUID());
                require(actor != null, "Sound placement lacks its actual integrated-server player");
                ItemStack held = actor.getMainHandItem().copy();
                try {
                    actor.setItemInHand(InteractionHand.MAIN_HAND, preset);
                    require(((BlockItem) preset.getItem()).place(new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, preset,
                        new BlockHitResult(Vec3.atCenterOf(robotPos), Direction.UP, robotPos, false))).consumesAction(), "Real Robot BlockItem placement failed");
                } finally { actor.setItemInHand(InteractionHand.MAIN_HAND, held); }
                require(level.getBlockEntity(robotPos) instanceof Robot, "Actual placed sound robot lacks its SDK interface");
                robot = (Robot) level.getBlockEntity(robotPos);
                movedPos = robotPos.relative(robot.toGlobal(Direction.SOUTH));
            }, server);
        }

        ItemStack eeprom() { return li.cil.oc.api.Items.registerEEPROM("Computer sound native BIOS", BIOS, new byte[0], false); }

        void serverTick() {
            if (placement == null || !placement.isDone() || placement.isCompletedExceptionally() || computer == null || robot == null) return;
            if (computer.machine().node().network() == null || robot.machine().node().network() == null) return;
            require(computer.machine().node() instanceof Connector && robot.machine().node() instanceof Connector, "Actual sound hosts lack their native power connectors");
            ((Connector) computer.machine().node()).changeBuffer(Double.POSITIVE_INFINITY);
            ((Connector) robot.machine().node()).changeBuffer(Double.POSITIVE_INFINITY);
            if (!machinesStarted) {
                require(computer.machine().start(), "Real Case native BIOS did not start: " + computer.machine().lastError());
                require(robot.machine().start(), "Real Robot native BIOS did not start: " + robot.machine().lastError());
                for (Node node : robot.machine().node().network().nodes()) if (node instanceof Component component && "robot".equals(component.name()) && component.canBeSeenFrom(robot.machine().node())) control = component;
                require(control != null, "Real moving Robot has no visible control component");
                machinesStarted = true;
            }
            require(computer.machine().lastError() == null && robot.machine().lastError() == null, "Actual native sound host crashed: Case=" + computer.machine().lastError() + ", Robot=" + robot.machine().lastError());
            serverReady = true;
        }

        boolean active(SoundInstance sound) {
            Channel channel = sound == null ? null : channels.get(sound);
            return channel != null && !channel.stopped();
        }
        String state(SoundInstance sound) {
            if (sound == null) return "none";
            Channel channel = channels.get(sound);
            return "soundManagerActive=" + client.getSoundManager().isActive(sound) + ", physicalChannel=" + (channel == null ? "none" : "stopped=" + channel.stopped() + "/playing=" + channel.playing()) + ", volume=" + sound.getVolume() + ", starts=" + starts(sound);
        }
        String snapshot() {
            return "Case[" + state(caseVoice) + "], Robot[" + state(robotVoice) + "], paused=" + client.isPaused() + ", screen=" + (client.screen == null ? "none" : client.screen.getClass().getName()) + ", BLOCKS=" + client.options.getSoundSourceVolume(SoundSource.BLOCKS) + ", MASTER=" + client.options.getSoundSourceVolume(SoundSource.MASTER) + ", pauseOnLostFocus=" + client.options.pauseOnLostFocus;
        }
        boolean at(SoundInstance sound, BlockPos position) { return sound.getX() == position.getX() && sound.getY() == position.getY() && sound.getZ() == position.getZ(); }
        SoundInstance voice(BlockPos position, SoundInstance excluded) {
            for (int i = observed.size() - 1; i >= 0; i--) {
                SoundInstance sound = observed.get(i);
                if (sound != excluded && at(sound, position) && active(sound)) return sound;
            }
            return null;
        }
        int starts(SoundInstance sound) { return (int) observed.stream().filter(value -> value == sound).count(); }
        void volume(SoundInstance sound) {
            require(sound.isLooping() && sound.getSource() == SoundSource.BLOCKS && Math.abs(sound.getVolume() - expectedVolume) < 1e-6,
                "Actual computer loop/category/volume mismatch: " + sound + " volume=" + sound.getVolume());
        }
        boolean commandDone() { if (!command.isDone()) return false; command.join(); return true; }

        Object readField(Class<?> owner, Object target, String name) throws ReflectiveOperationException {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        }
        void bookkeeping() throws ReflectiveOperationException {
            Object engine = readField(SoundManager.class, client.getSoundManager(), "soundEngine");
            List<?> tickers = (List<?>) readField(SoundEngine.class, engine, "tickingSounds");
            Map<?, ?> deadlines = (Map<?, ?>) readField(SoundEngine.class, engine, "soundDeleteTime");
            var sources = (com.google.common.collect.Multimap<?, ?>) readField(SoundEngine.class, engine, "instanceBySource");
            for (SoundInstance sound : List.of(caseVoice, robotVoice)) {
                long tickerCount = tickers.stream().filter(entry -> entry == sound).count();
                long sourceCount = sources.values().stream().filter(entry -> entry == sound).count();
                require(tickerCount == 1 && sourceCount == 1 && deadlines.containsKey(sound),
                    "Actual native running source indexes differ after mute cycle " + (muteCycles + 1) + ": tickerCount=" + tickerCount + ", sourceCount=" + sourceCount + ", deadline=" + deadlines.containsKey(sound));
            }
            require(tickers.stream().noneMatch(entry -> entry == stoppedCaseVoice) && !deadlines.containsKey(stoppedCaseVoice)
                    && sources.values().stream().noneMatch(entry -> entry == stoppedCaseVoice),
                "Actual stopped old Case voice retains native ticker/deadline/source index after cycle " + (muteCycles + 1));
        }

        void tick() throws ReflectiveOperationException {
            if (finishing) return;
            if (System.nanoTime() - started >= TimeUnit.MINUTES.toNanos(2)) throw new AssertionError("Computer sound contract timed out phase=" + phase + ", observed=" + observed.size() + ", " + snapshot());
            if (serverError != null) throw new IllegalStateException("Real sound host operation failed", serverError);
            if (placement.isDone()) placement.join();
            switch (phase) {
                case 0 -> {
                    if (!placement.isDone() || !serverReady) return;
                    placement.join();
                    caseVoice = voice(casePos, null); robotVoice = voice(robotPos, null);
                    if (caseVoice == null || robotVoice == null) return;
                    volume(caseVoice); volume(robotVoice);
                    robotStarts = starts(robotVoice);
                    results.add("PASS C22.sound.running real native-Lua Case and Robot snapshots start native looping BLOCKS instances at configured half volume");
                    command = CompletableFuture.runAsync(() -> {
                        try {
                            String address = robot.machine().node().address();
                            Object[] result = control.invoke("move", robot.machine(), 3);
                            require(result != null && result.length > 0 && Boolean.TRUE.equals(result[0]), "Actual robot.move rejected real empty target: " + java.util.Arrays.deepToString(result));
                            require(level.getBlockEntity(movedPos) instanceof Robot, "Actual move produced no target Robot proxy");
                            robot = (Robot) level.getBlockEntity(movedPos);
                            require(address.equals(robot.machine().node().address()), "Actual Robot movement replaced the stable machine identity");
                        } catch (Exception error) { throw new IllegalStateException(error); }
                    }, server);
                    phase = 1;
                }
                case 1 -> {
                    if (!commandDone() || client.level.getBlockEntity(movedPos) == null || !at(robotVoice, movedPos)) return;
                    require(active(robotVoice) && starts(robotVoice) == robotStarts, "Actual moving Robot restarted/stopped its existing native loop");
                    results.add("PASS C22.sound.move actual robot.move/proxy packets retarget the same active sound instance without replay");
                    command = CompletableFuture.runAsync(() -> require(computer.machine().stop(), "Actual Case did not stop"), server);
                    phase = 2;
                }
                case 2 -> {
                    if (!commandDone() || active(caseVoice)) return;
                    require(active(robotVoice), "Stopping the separate real Case also silenced Robot");
                    stoppedCaseVoice = caseVoice;
                    command = CompletableFuture.runAsync(() -> require(computer.machine().start(), "Actual Case did not restart"), server);
                    phase = 3;
                }
                case 3 -> {
                    if (!commandDone()) return;
                    SoundInstance restarted = voice(casePos, caseVoice);
                    if (restarted == null) return;
                    caseVoice = restarted; volume(caseVoice);
                    results.add("PASS C22.sound.stop real Case stop releases its voice; native restart creates a new independent loop");
                    caseStarts = starts(caseVoice); robotStarts = starts(robotVoice);
                    client.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).set(0.0);
                    phase = 4;
                }
                case 4 -> {
                    require(client.options.getSoundSourceVolume(SoundSource.BLOCKS) == 0, "Actual BLOCKS mute did not apply");
                    require(!client.isPaused(), "BLOCKS channel cleanup requires actual game ticks; " + snapshot());
                    // Leave zero volume in place for a complete native engine tick;
                    // its bookkeeping API can retain true after the channels stop.
                    if (++muteTicks < 2 || active(caseVoice) || active(robotVoice)) return;
                    client.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).set(1.0);
                    phase = 5;
                }
                case 5 -> {
                    if (!active(caseVoice) || !active(robotVoice) || starts(caseVoice) <= caseStarts || starts(robotVoice) <= robotStarts) return;
                    volume(caseVoice); volume(robotVoice);
                    bookkeeping();
                    results.add("PASS C22.sound.volume cycle=" + (++muteCycles) + "/3 actual BLOCKS zero stops native channels and unmute restores them; native tickingSounds/source index each contain one current instance and stopped old Case has no ticker/deadline/source residue");
                    if (muteCycles < 3) {
                        caseStarts = starts(caseVoice); robotStarts = starts(robotVoice); muteTicks = 0;
                        client.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).set(0.0);
                        phase = 4;
                        return;
                    }
                    client.setScreen(new PauseScreen(true));
                    phase = 6;
                }
                case 6 -> {
                    if (!client.isPaused() || caseVoice.getVolume() != 0 || robotVoice.getVolume() != 0) return;
                    require(active(caseVoice) && active(robotVoice), "Actual integrated pause discarded the live computer sources");
                    client.setScreen(null);
                    phase = 7;
                }
                case 7 -> {
                    if (client.isPaused() || Math.abs(caseVoice.getVolume() - expectedVolume) > 1e-6 || Math.abs(robotVoice.getVolume() - expectedVolume) > 1e-6) return;
                    require(active(caseVoice) && active(robotVoice), "Actual resume did not retain native loop instances");
                    results.add("PASS C22.sound.pause actual integrated PauseScreen silences live instances and closing it restores configured volume");
                    caseStarts = starts(caseVoice); robotStarts = starts(robotVoice);
                    client.getSoundManager().reload();
                    phase = 8;
                }
                case 8 -> {
                    if (!active(caseVoice) || !active(robotVoice) || starts(caseVoice) <= caseStarts || starts(robotVoice) <= robotStarts) return;
                    results.add("PASS C22.sound.reload actual native SoundManager reload recreates channels for the same running host instances");
                    command = CompletableFuture.runAsync(() -> {
                        computer.machine().stop(); robot.machine().stop();
                        computer.clearContent(); robot.clearContent();
                        require(level.setBlock(casePos, Blocks.AIR.defaultBlockState(), 3), "Actual Case removal failed");
                        require(level.setBlock(movedPos, Blocks.AIR.defaultBlockState(), 3), "Actual Robot removal failed");
                    }, server);
                    phase = 9;
                }
                case 9 -> {
                    if (!commandDone() || client.level.getBlockEntity(casePos) != null || client.level.getBlockEntity(movedPos) != null || active(caseVoice) || active(robotVoice)) return;
                    results.add("PASS C22.sound.remove real block removal unloads both client hosts and releases their native sound channels");
                    results.add("LIMIT C22.sound: physical native-channel/API lifecycle proof; listening quality, real chunk/world logout, multiplayer and the other C22 particles/pets/clipboard remain separate acceptance");
                    finish();
                }
                default -> throw new AssertionError("Unknown computer sound phase " + phase);
            }
        }

        void fail(Throwable error) {
            if (finishing) return;
            results.add("FAIL C22.sound phase=" + phase + ": " + error);
            finish();
        }

        void finish() {
            if (finishing) return;
            finishing = true;
            if (client.screen instanceof PauseScreen) client.setScreen(null);
            client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set((double) originalMaster);
            client.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).set((double) originalBlocks);
            cleanup = CompletableFuture.runAsync(() -> {
                if (computer != null) computer.machine().stop();
                if (robot != null) robot.machine().stop();
                for (var entry : restored.entrySet()) {
                    if (level.getBlockEntity(entry.getKey()) instanceof net.minecraft.world.Container inventory) inventory.clearContent();
                    level.setBlock(entry.getKey(), entry.getValue(), 3);
                }
                for (long chunk : forced) level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            }, server);
        }
    }
}
