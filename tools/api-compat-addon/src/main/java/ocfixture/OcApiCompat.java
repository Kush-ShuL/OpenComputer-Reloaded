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
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
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
            LOGGER.info("API_FIXTURE SDK={} owner={} registered", API.VERSION, API.ID_OWNER);
        }));
        bus.addListener((InterModEnqueueEvent event) -> {
            li.cil.oc.api.IMC.registerProgramDiskLabel("oc_fixture_program", "fixture-disk");
            ProductionContract.enqueue();
        });
        NeoForge.EVENT_BUS.register(OcApiCompat.class);
    }
    public static String run(ServerLevel level) throws Exception {
        if (RuntimeContract.isRunning()) throw new IllegalStateException("A fixture run is already active");
        finished = false;
        BaseContract.run(level);
        CheckpointContract.run(level);
        ProductionContract.run(level);
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
        if (!Boolean.getBoolean("ocfixture.autorun")) return;
        AUTOMATED.set(true);
        try { LOGGER.info("API_FIXTURE {}", run(event.getServer().overworld())); }
        catch (Throwable error) { LOGGER.error("API_FIXTURE FAIL startup", error); throw new IllegalStateException("External API fixture failed", error); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        RuntimeContract.tick(); BaseContract.tick(); CheckpointContract.tick(); ProductionContract.tick();
        if (!AUTOMATED.get() || finished || RuntimeContract.isRunning() || BaseContract.isRunning() || CheckpointContract.isRunning() || ProductionContract.isRunning()) return;
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
    public static List<String> results() { return java.util.stream.Stream.of(BaseContract.results(), RuntimeContract.results(), CheckpointContract.results(), ProductionContract.results()).flatMap(List::stream).toList(); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { RuntimeContract.close(); BaseContract.close(); CheckpointContract.close(); ProductionContract.close(); AUTOMATED.set(false); }
}
