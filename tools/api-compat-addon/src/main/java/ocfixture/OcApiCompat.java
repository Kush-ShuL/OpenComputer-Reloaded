package ocfixture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import li.cil.oc.api.API;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A separately compiled consumer of the published SDK, with no OC implementation dependencies. */
@Mod("oc_api_compat")
public final class OcApiCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-api-compat");
    private static final AtomicBoolean AUTOMATED = new AtomicBoolean();
    private static boolean finished;
    public OcApiCompat(IEventBus bus) {
        FixtureBlocks.register(bus);
        WorldPersistenceContract.registerBlocks(bus);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            if (HostPersistenceContract.enabled()) { HostPersistenceContract.register(); return; }
            if (DeviceContract.enabled()) return;
            if (MekanismContract.enabled()) return;
            if (ae2Scenario()) return;
            if (EnderStorageContract.enabled()) return;
            if (ProjectRedContract.enabled()) return;
            if (JadeContract.enabled()) return;
            if (API.driver == null || API.network == null) throw new AssertionError("OC facades unavailable during enqueued common setup");
            if (!ModList.get().isLoaded(API.ID_OWNER)) throw new AssertionError("SDK API.ID_OWNER differs from runtime mod metadata: " + API.ID_OWNER);
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                if (API.manual == null) throw new AssertionError("Client Manual API unavailable during addon common setup");
                li.cil.oc.api.Manual.addProvider((li.cil.oc.api.manual.ContentProvider) path -> path.endsWith("ocfixture/api.md") ? List.of("# External API fixture") : null);
                var content = li.cil.oc.api.Manual.contentFor("/ocfixture/api.md");
                if (content == null || !content.iterator().hasNext()) throw new AssertionError("Client Manual provider registration was discarded");
                LOGGER.info("API_FIXTURE PASS early client Manual provider registration");
            } else if (API.manual != null) throw new AssertionError("Dedicated server loaded a client Manual backend");
            BaseContract.register();
            RuntimeContract.register();
            CheckpointContract.register();
            ProductionContract.register();
            if (computerCraftLoaded()) {
                verifyComputerCraftSelection();
                ComputerCraftContract.register();
            }
            LOGGER.info("API_FIXTURE SDK={} owner={} registered", API.VERSION, API.ID_OWNER);
        }));
        bus.addListener((InterModEnqueueEvent event) -> {
            if (HostPersistenceContract.enabled()) return;
            if (DeviceContract.enabled()) return;
            if (MekanismContract.enabled()) return;
            if (ae2Scenario()) return;
            if (EnderStorageContract.enabled()) return;
            if (ProjectRedContract.enabled()) return;
            if (JadeContract.enabled()) return;
            li.cil.oc.api.IMC.registerProgramDiskLabel("oc_fixture_program", "fixture-disk");
            ProductionContract.enqueue();
        });
        NeoForge.EVENT_BUS.register(OcApiCompat.class);
    }
    public static String run(ServerLevel level) throws Exception {
        if (RuntimeContract.isRunning()) throw new IllegalStateException("A fixture run is already active");
        finished = false;
        if (JadeContract.enabled()) return JadeContract.run(level);
        if (ProjectRedContract.enabled()) return ProjectRedContract.run(level);
        if (EnderStorageContract.enabled()) return EnderStorageContract.run(level);
        if (ae2Scenario()) return AppliedEnergisticsContract.run(level);
        if (MekanismContract.enabled()) return MekanismContract.run(level);
        if (Boolean.getBoolean("ocfixture.computerCraftOnly")) {
            if (!computerCraftLoaded()) throw new IllegalStateException("The requested optional ComputerCraft runtime mod was not loaded");
            verifyComputerCraftSelection();
            return computerCraftAllowed() ? ComputerCraftContract.run(level) : ComputerCraftContract.runDisabled(level);
        }
        BaseContract.run(level);
        CheckpointContract.run(level);
        ProductionContract.run(level);
        if (computerCraftLoaded()) {
            if (computerCraftAllowed()) ComputerCraftContract.run(level);
            else ComputerCraftContract.runDisabled(level);
        }
        return RuntimeContract.run(level);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("oc_api_compat").requires(source -> source.hasPermission(2))
                .then(Commands.literal("verify").executes(context -> {
                    try { String message = run(context.getSource().getLevel()); context.getSource().sendSuccess(() -> Component.literal(message), false); return 1; }
                    catch (Throwable error) { LOGGER.error("API_FIXTURE FAIL", error); context.getSource().sendFailure(Component.literal(error.toString())); return 0; }
                }))
                .then(Commands.literal("results").executes(context -> {
                    for (String result : results()) context.getSource().sendSuccess(() -> Component.literal(result), false);
                    return 1;
                })));
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (HostPersistenceContract.enabled()) { HostPersistenceContract.start(event.getServer()); return; }
        if (DeviceContract.enabled()) { DeviceContract.start(event.getServer()); return; }
        if (WorldPersistenceContract.enabled()) { WorldPersistenceContract.start(event.getServer()); return; }
        if (!Boolean.getBoolean("ocfixture.autorun")) return;
        AUTOMATED.set(true);
        try { LOGGER.info("API_FIXTURE {}", run(event.getServer().overworld())); }
        catch (Throwable error) { LOGGER.error("API_FIXTURE FAIL startup", error); throw new IllegalStateException("External API fixture failed", error); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (HostPersistenceContract.enabled()) { HostPersistenceContract.tick(); return; }
        if (DeviceContract.enabled()) { DeviceContract.tick(); return; }
        if (WorldPersistenceContract.enabled()) { WorldPersistenceContract.tick(event.getServer()); return; }
        if (JadeContract.enabled()) JadeContract.tick();
        else if (ProjectRedContract.enabled()) ProjectRedContract.tick();
        else if (EnderStorageContract.enabled()) EnderStorageContract.tick();
        else if (ae2Scenario()) AppliedEnergisticsContract.tick();
        else if (MekanismContract.enabled()) MekanismContract.tick();
        else { RuntimeContract.tick(); BaseContract.tick(); CheckpointContract.tick(); ProductionContract.tick(); }
        if (computerCraftLoaded()) ComputerCraftContract.tick();
        if (!AUTOMATED.get() || finished || JadeContract.isRunning() || ProjectRedContract.isRunning() || EnderStorageContract.isRunning() || ae2Scenario() && AppliedEnergisticsContract.isRunning() || MekanismContract.isRunning() || RuntimeContract.isRunning() || BaseContract.isRunning() || CheckpointContract.isRunning() || ProductionContract.isRunning() || computerCraftLoaded() && ComputerCraftContract.isRunning()) return;
        finished = true;
        boolean failure = results().stream().anyMatch(result -> result.startsWith("FAIL"));
        for (String result : results()) LOGGER.info("API_FIXTURE {}", result);
        LOGGER.info("API_FIXTURE_COMPLETE status={}", failure ? "FAIL" : "PASS");
        writeReport(failure);
        if (failure) throw new AssertionError("External API compatibility fixture failed; see API_FIXTURE results");
        if (Boolean.getBoolean("ocfixture.stopAfterRun")) event.getServer().halt(false);
    }
    private static void writeReport(boolean failure) {
        String target = System.getProperty("ocfixture.reportFile");
        if (target == null) return;
        List<String> lines = new ArrayList<>();
        lines.add("status=" + (failure ? "FAIL" : "PASS"));
        lines.addAll(results());
        try {
            Path path = Path.of(target);
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException error) { throw new UncheckedIOException("Could not write the API contract report", error); }
    }
    private static boolean ae2Scenario() { return Boolean.getBoolean("ocfixture.ae2Only"); }
    private static boolean computerCraftLoaded() { return ModList.get().isLoaded("computercraft"); }
    private static boolean computerCraftEnabled() {
        return API.config != null && API.config.hasPath("integration.computerCraft.enabled")
                && API.config.getBoolean("integration.computerCraft.enabled");
    }
    private static boolean computerCraftAllowed() {
        return computerCraftEnabled() && (!API.config.hasPath("integration.modBlacklist")
                || API.config.getStringList("integration.modBlacklist").stream().noneMatch(id -> id.equalsIgnoreCase("computercraft")));
    }
    private static void verifyComputerCraftSelection() {
        String expected = System.getProperty("ocfixture.enableComputerCraft");
        if (expected != null && computerCraftEnabled() != Boolean.parseBoolean(expected))
            throw new AssertionError("Isolated ComputerCraft configuration differs from explicit ocEnableComputerCraft selection");
    }
    public static List<String> results() { if(JadeContract.enabled())return JadeContract.results();if(ProjectRedContract.enabled())return ProjectRedContract.results();if(EnderStorageContract.enabled())return EnderStorageContract.results();if(ae2Scenario())return AppliedEnergisticsContract.results();if(MekanismContract.enabled())return MekanismContract.results();return java.util.stream.Stream.of(BaseContract.results(), RuntimeContract.results(), CheckpointContract.results(), ProductionContract.results(), computerCraftLoaded() ? ComputerCraftContract.results() : List.<String>of()).flatMap(List::stream).toList(); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(JadeContract.enabled()){JadeContract.close();AUTOMATED.set(false);return;}if(ProjectRedContract.enabled()){ProjectRedContract.close();AUTOMATED.set(false);return;}if(EnderStorageContract.enabled()){EnderStorageContract.close();AUTOMATED.set(false);return;}if(ae2Scenario()){AppliedEnergisticsContract.close();AUTOMATED.set(false);return;}if(MekanismContract.enabled()){MekanismContract.close();AUTOMATED.set(false);return;}if (HostPersistenceContract.enabled()) { HostPersistenceContract.stopped(); return; } if (DeviceContract.enabled()) { DeviceContract.stopped(); return; } WorldPersistenceContract.stopped(); RuntimeContract.close(); BaseContract.close(); CheckpointContract.close(); ProductionContract.close(); if (computerCraftLoaded()) ComputerCraftContract.close(); AUTOMATED.set(false); }
}
