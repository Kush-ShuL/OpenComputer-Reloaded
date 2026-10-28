package ocfixture;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates the requested player save once, then leaves its ordinary client interactive. */
public final class ShowcaseClient {
    public static final String WORLD = "OC-Showcase-20261028";
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-showcase");
    private static final List<String> evidence = new ArrayList<>();
    private static Path gameDirectory, worldDirectory, report, screenshot;
    private static CompletableFuture<ShowcaseBase.Report> construction;
    private static CompletableFuture<Void> save;
    private static int phase, settledFrames;
    private static boolean existing, finished;
    private ShowcaseClient() {}

    public static void register() {
        gameDirectory = Path.of(System.getProperty("ocfixture.showcaseDirectory")).toAbsolutePath().normalize();
        worldDirectory = gameDirectory.resolve("saves").resolve(WORLD);
        report = Path.of(System.getProperty("ocfixture.showcaseReport")).toAbsolutePath().normalize();
        screenshot = report.resolveSibling("showcase-base-overview.png");
        NeoForge.EVENT_BUS.addListener(ShowcaseClient::tick);
        NeoForge.EVENT_BUS.addListener(ShowcaseClient::rendered);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void tick(ClientTickEvent.Post event) {
        if (finished) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (phase == 0 && client.getOverlay() == null && client.screen instanceof TitleScreen) {
                require(client.gameDirectory.toPath().toRealPath().equals(gameDirectory.toRealPath()), "Showcase client has a different game directory");
                existing = Files.isRegularFile(worldDirectory.resolve("level.dat"));
                if (existing) {
                    LOGGER.info("SHOWCASE opening existing save {} without rebuilding", worldDirectory);
                    client.createWorldOpenFlows().openWorld(WORLD, () -> client.setScreen(new TitleScreen()));
                } else {
                    require(!Files.exists(worldDirectory), "The showcase folder already exists without level.dat; it will not be overwritten");
                    LOGGER.info("SHOWCASE creating fresh player save {}", worldDirectory);
                    client.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings("开放式电脑：综合基地", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(258943363L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                        new TitleScreen());
                }
                phase = 1;
            } else if (phase == 1 && client.player != null && client.level != null && client.screen == null && client.getOverlay() == null) {
                var server = client.getSingleplayerServer();
                require(server != null, "Showcase requires its local integrated server");
                require(server.getWorldPath(LevelResource.ROOT).toRealPath().equals(worldDirectory.toRealPath()), "Refusing to build in a different save");
                if (existing && !Boolean.getBoolean("ocfixture.showcaseResumeBuild") && !Boolean.getBoolean("ocfixture.showcaseRepairPrint")) {
                    LOGGER.info("SHOWCASE_READY existing=true world={}; client remains interactive", worldDirectory);
                    finished = true;
                    return;
                }
                var playerId = client.player.getUUID();
                construction = CompletableFuture.supplyAsync(() -> {
                    var player = java.util.Objects.requireNonNull(server.getPlayerList().getPlayer(playerId));
                    if (Boolean.getBoolean("ocfixture.showcaseRepairPrint")) return ShowcaseBase.repairPrint(server.overworld(), player, worldDirectory);
                    return existing ? ShowcaseBase.resume(server.overworld(), player, worldDirectory)
                        : ShowcaseBase.build(server.overworld(), player, worldDirectory);
                }, server).thenCompose(value -> value);
                phase = 2;
            } else if (phase == 2 && construction.isDone()) {
                ShowcaseBase.Report built = construction.join();
                evidence.add("world=" + worldDirectory);
                evidence.add("construction=" + built);
                var server = client.getSingleplayerServer();
                save = CompletableFuture.runAsync(() -> require(server.saveEverything(false, true, true), "Minecraft did not save the new showcase world"), server);
                phase = 3;
            } else if (phase == 3 && save.isDone()) {
                save.join();
                evidence.add("saved=true");
                write("PASS");
                settledFrames = 0;
                phase = 4;
            }
        } catch (Throwable error) {
            fail(client, error);
        }
    }

    private static void rendered(RenderFrameEvent.Post event) {
        if (finished || phase != 4) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.screen != null || client.getOverlay() != null || ++settledFrames < 45) return;
        try (NativeImage pixels = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            Files.createDirectories(report.getParent());
            pixels.writeToFile(screenshot);
            evidence.add("screenshot=" + screenshot);
            evidence.add("interactive=true");
            write("PASS");
            finished = true;
            client.player.displayClientMessage(Component.literal("综合基地已保存，可以开始体验。手册和工具已放入背包。"), false);
            LOGGER.info("SHOWCASE_READY world={} report={} screenshot={}; client remains interactive", worldDirectory, report, screenshot);
        } catch (Throwable error) {
            fail(client, error);
        }
    }

    private static void write(String status) throws Exception {
        Files.createDirectories(report.getParent());
        var lines = new ArrayList<String>();
        lines.add("status=" + status);
        lines.addAll(evidence);
        Files.write(report, lines, StandardCharsets.UTF_8);
    }

    private static void fail(Minecraft client, Throwable error) {
        finished = true;
        LOGGER.error("SHOWCASE creation failed; client remains open", error);
        evidence.add("error=" + error);
        try { write("FAIL"); } catch (Exception reportError) { LOGGER.error("SHOWCASE report could not be written", reportError); }
        if (client.player != null) client.player.displayClientMessage(Component.literal("综合基地准备失败，请查看启动日志；现有存档已保留。"), false);
    }
}
