package ocfixture;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import li.cil.oc.api.API;
import li.cil.oc.api.Items;
import li.cil.oc.api.Manual;
import li.cil.oc.api.manual.ContentProvider;
import li.cil.oc.api.manual.InteractiveImageRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real manual screens in this process's own isolated, normally loaded single-player world. */
@OnlyIn(Dist.CLIENT)
public final class PlayerManualClientContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-player-manual-client-contract");
    private static final String HOME = "%LANGUAGE%/index.md";
    private static final String EXTENSION = "/ocfixture/manual.md", AFTER = "/ocfixture/after.md";
    private static final List<String> EXTENSION_CONTENT = List.of(
        "# Published SDK extension", "`function sample(n)`", "`  return n + 1`", "`end`",
        "![Original image](opencomputers:/textures/gui/printer_material.png)",
        "[![Click feedback](ocfixture:manual)](after.md)",
        "The green image handles its first click.",
        "Its next click follows the enclosing link.", "", "Scroll and history retain this page's position.",
        "Line 1", "Line 2", "Line 3", "Line 4", "Line 5", "Line 6", "Line 7", "Line 8", "Line 9");
    private static final List<String> results = new ArrayList<>();
    private static final ProbeImage image = new ProbeImage();
    private static Path report, directory;
    private static String runId;
    private static long started;
    private static int phase, frame, phaseFrame, tabRenders, olderImageCalls;
    private static float tabX, tabY;
    private static boolean registered, finished;
    private static BlockPos contextPosition;
    private static CompletableFuture<Void> contextPlacement;
    private static ItemStack oldHand = ItemStack.EMPTY;

    private PlayerManualClientContract() {}

    /** Called only by the separately selected, isolated physical-client run. */
    public static void register() {
        if (registered) return;
        report = Path.of(requiredProperty("ocfixture.manualClientReport")).toAbsolutePath().normalize();
        directory = Path.of(requiredProperty("ocfixture.manualClientDirectory")).toAbsolutePath().normalize();
        runId = requiredProperty("ocfixture.manualClientRunId");
        require(report.getParent().equals(directory), "The report must belong to this isolated run directory");
        registered = true;
        started = System.nanoTime();
        NeoForge.EVENT_BUS.addListener(PlayerManualClientContract::tick);
        NeoForge.EVENT_BUS.addListener(PlayerManualClientContract::rendered);
    }

    private static void tick(ClientTickEvent.Post event) {
        if (finished) return;
        Minecraft client = Minecraft.getInstance();
        try {
            require(System.nanoTime() - started < TimeUnit.MINUTES.toNanos(5),
                "Timed out in phase " + phase + "; screen=" + (client.screen == null ? "null" : client.screen.getClass().getName()));
            if (phase == 0 && client.getOverlay() == null && client.screen instanceof TitleScreen) {
                require(client.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()),
                    "This process is not using the selected isolated run directory");
                require(!Files.exists(directory.resolve("saves/manual-" + runId)), "Use a fresh manual-client run ID");
                require(API.manual != null, "Actual client Manual backend is unavailable");
                verifyResources(client);
                registerProviders();
                advance(1);
                LevelSettings settings = new LevelSettings("Manual client " + runId, GameType.CREATIVE,
                    false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("manual-" + runId, settings,
                    new WorldOptions(258943363L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(), new TitleScreen());
            } else if (phase == 1 && client.player != null && client.level != null
                    && client.screen == null && client.getOverlay() == null) {
                require(client.hasSingleplayerServer(), "The test must own a normal integrated-server world");
                Manual.reset();
                Manual.openFor(client.player);
                advance(2);
            } else if (phase == 10 && contextPlacement.isDone()
                    && client.level.getBlockState(contextPosition).is(Items.get("case1").block())) {
                contextPlacement.join();
                String path = Manual.pathFor(client.level, contextPosition);
                require("%LANGUAGE%/block/case1.md".equals(path), "Actual Case context resolved to " + path);
                oldHand = client.player.getMainHandItem().copy();
                client.player.setItemInHand(InteractionHand.MAIN_HAND, Items.get("manual").createItemStack(1));
                ItemStack manual = client.player.getMainHandItem();
                UseOnContext context = new UseOnContext(client.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(contextPosition), Direction.UP, contextPosition, false));
                require(manual.getItem().useOn(context).consumesAction(), "Actual manual item rejected a documented Case");
                require(path.equals(currentPath()) && manualScreen(client) != null, "Manual block use did not open its real context page");
                results.add("PASS T03.context real integrated-world Case -> SDK pathFor -> actual manual Item.useOn -> " + path);
                advance(11);
            }
        } catch (Throwable error) { finish(client, error); }
    }

    /** Every screenshot and input stage follows real frames rendered by Minecraft. */
    private static void rendered(RenderFrameEvent.Post event) {
        frame++;
        if (finished || phase < 2 || phase == 10 || frame - phaseFrame < 2) return;
        Minecraft client = Minecraft.getInstance();
        try {
            Screen screen = manualScreen(client);
            switch (phase) {
                case 2 -> {
                    require(HOME.equals(currentPath()), "Normal Manual.openFor did not retain the reset home page");
                    require(tabRenders > 0, "The actual screen did not render the registered SDK tab");
                    require(screen.mouseClicked(tabX + 8, tabY + 8, 0), "Actual screen did not handle the observed SDK tab click");
                    require(EXTENSION.equals(currentPath()), "Clicking the real SDK tab did not navigate to its page");
                    results.add("PASS T03.open/tab actual player openFor -> home -> rendered SDK tab -> extension page");
                    advance(3);
                }
                case 3 -> {
                    require(image.renders > 0, "Actual Markdown rendering never invoked the SDK image");
                    double x = image.screenX + image.screenWidth / 2, y = image.screenY + image.screenHeight / 2;
                    require(screen.mouseClicked(x, y, 0), "Actual screen rejected the observed image click");
                    require(image.clicks == 1 && EXTENSION.equals(currentPath()), "Handled interactive image click followed its enclosing link");
                    require(Math.abs(image.clickedX - image.getWidth() / 2) <= 1
                            && Math.abs(image.clickedY - image.getHeight() / 2) <= 1,
                        "Interactive image received incorrect natural-image click coordinates");
                    image.consume = false;
                    require(screen.mouseClicked(x, y, 0), "Actual screen rejected the unhandled image click");
                    require(image.clicks == 2 && AFTER.equals(currentPath()), "Unhandled image click did not follow its relative enclosing link");
                    screen.mouseClicked(x, y, 1);
                    require(EXTENSION.equals(currentPath()), "Right click did not return to the extension page");
                    screen.mouseScrolled(x, y, 0, -1);
                    int savedOffset = currentOffset();
                    require(savedOffset > 0, "Actual screen wheel did not scroll long content");
                    Manual.navigate(AFTER);
                    screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
                    require(EXTENSION.equals(currentPath()) && currentOffset() == savedOffset,
                        "Screen back navigation did not restore the actual page scroll offset");
                    screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
                    require(currentOffset() == 0, "Screen Home key did not reset page scrolling");
                    results.add("PASS T03.providers first ContentProvider/last ImageProvider precedence; actual image click consumed then followed relative link; wheel/back restored offset=" + savedOffset);
                    advance(4);
                }
                case 4 -> {
                    screenshot(client, "sdk-code-and-images.png");
                    String path = Manual.pathFor(Items.get("tablet").createItemStack(1));
                    require("%LANGUAGE%/item/tablet.md".equals(path), "SDK tablet item context resolved to " + path);
                    Manual.navigate(path);
                    advance(5);
                }
                case 5 -> {
                    require("%LANGUAGE%/item/tablet.md".equals(currentPath()), "Actual tablet context page was not retained");
                    screenshot(client, "tablet-legacy-image.png");
                    Manual.navigate("%LANGUAGE%/item/hoverupgrade1.md");
                    screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
                    require(currentOffset() > 0, "Robot movement page did not scroll to its final diagram");
                    advance(6);
                }
                case 6 -> {
                    screenshot(client, "robot-movement.png");
                    Manual.navigate("%LANGUAGE%/general/example.md");
                    advance(7);
                }
                case 7 -> {
                    screen.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
                    require(currentOffset() > 0, "Original Markdown example did not respond to Page Down");
                    int savedOffset = currentOffset();
                    Manual.navigate("%LANGUAGE%/item/tablet.md");
                    screen.mouseClicked(screen.width / 2D, screen.height / 2D, 1);
                    require("%LANGUAGE%/general/example.md".equals(currentPath()) && currentOffset() == savedOffset,
                        "Original example page lost its actual scroll history");
                    results.add("PASS T03.original-pages actual tablet/hover-upgrade/example screen render, PageDown and right-click history");
                    // Place a real server block and wait for the actual client chunk update before item use.
                    // Closing the pause screen lets the integrated server deliver its normal chunk update.
                    screen.onClose();
                    contextPosition = client.player.blockPosition().east(3);
                    var server = client.getSingleplayerServer();
                    contextPlacement = CompletableFuture.runAsync(() -> server.overworld().setBlock(contextPosition,
                        Items.get("case1").block().defaultBlockState(), 3), server);
                    advance(10);
                }
                case 11 -> {
                    String contextPath = currentPath();
                    screen.onClose();
                    ItemStack manual = client.player.getMainHandItem();
                    manual.getItem().use(client.level, client.player, InteractionHand.MAIN_HAND);
                    require(contextPath.equals(currentPath()) && manualScreen(client) != null,
                        "Ordinary manual item use discarded the current page");
                    // LocalPlayer queries its real public Input, not Entity's synchronized shift flag.
                    boolean oldShift = client.player.input.shiftKeyDown;
                    client.player.input.shiftKeyDown = true;
                    try {
                        require(client.player.isShiftKeyDown(), "Actual LocalPlayer input did not enter sneaking state");
                        manual.getItem().use(client.level, client.player, InteractionHand.MAIN_HAND);
                    } finally { client.player.input.shiftKeyDown = oldShift; }
                    require(HOME.equals(currentPath()), "Sneaking manual item use did not reset to home");
                    manualScreen(client).mouseClicked(0, 0, 1);
                    require(client.screen == null, "Right click at the root did not close the real manual screen");
                    client.player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
                    results.add("PASS T03.item ordinary item use preserves context; sneaking item use resets home; root back closes GUI");
                    finish(client, null);
                }
                default -> { }
            }
        } catch (Throwable error) { finish(client, error); }
    }

    private static void registerProviders() {
        Manual.addProvider((ContentProvider) path -> EXTENSION.equals(path) ? EXTENSION_CONTENT
            : AFTER.equals(path) ? List.of("# Image link destination", "The unhandled image click reached this page.") : null);
        Manual.addProvider((ContentProvider) path -> EXTENSION.equals(path) ? List.of("WRONG_PROVIDER") : null);
        require(lines(Manual.contentFor(EXTENSION)).equals(EXTENSION_CONTENT), "ContentProvider registration order was not respected");
        Manual.addProvider("ocfixture", data -> { olderImageCalls++; return image; });
        Manual.addProvider("ocfixture", data -> "manual".equals(data) ? image : null);
        require(Manual.imageFor("ocfixture:manual") == image && olderImageCalls == 0,
            "Last matching ImageProvider did not take priority");
        require(Manual.imageFor("ocfixture:fallback") == image && olderImageCalls == 1,
            "A null ImageProvider result did not fall back to the previous provider");
        Manual.addTab(graphics -> {
            Vector3f origin = graphics.pose().last().pose().transformPosition(0, 0, 0, new Vector3f());
            tabX = origin.x; tabY = origin.y; tabRenders++;
            graphics.fill(0, 0, 16, 16, 0xFF36B85A);
        }, "SDK manual contract", EXTENSION);
    }

    private static void verifyResources(Minecraft client) throws Exception {
        for (String path : List.of(HOME, "%LANGUAGE%/general/example.md", "%LANGUAGE%/item/tablet.md",
                "%LANGUAGE%/item/hoverupgrade1.md")) require(!lines(Manual.contentFor(path)).isEmpty(), "Actual manual content is unavailable: " + path);
        require(lines(Manual.contentFor("%LANGUAGE%/item/tablet.md")).stream().anyMatch(line -> line.contains("item:OpenComputers:item@68")),
            "The original tablet page no longer exercises its legacy shared-item image reference");
        for (String path : List.of("item:OpenComputers:item@68", "item:OpenComputers:item@73", "item:OpenComputers:item@84")) {
            var renderer = Manual.imageFor(path);
            require(renderer != null && renderer.getWidth() == 32 && renderer.getHeight() == 32,
                "Legacy manual image did not resolve to the original live item renderer: " + path);
        }
        var resource = client.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("opencomputers", "doc/img/robotmovement.png"))
            .orElseThrow(() -> new AssertionError("Packaged lowercase robotmovement.png resource is missing"));
        try (var stream = resource.open(); var nativeImage = NativeImage.read(stream)) {
            var renderer = Manual.imageFor("opencomputers:doc/img/robotMovement.png");
            require(nativeImage.getWidth() == 320 && nativeImage.getHeight() == 320
                    && renderer != null && renderer.getWidth() == nativeImage.getWidth() && renderer.getHeight() == nativeImage.getHeight(),
                "Original mixed-case movement image URL did not resolve to the actual packaged PNG");
        }
        results.add("PASS T03.resources real loaded original example/tablet/hover pages; legacy item@68/@73/@84 live images; mixed-case robotMovement URL -> lowercase packaged 320x320 PNG");
    }

    private static Screen manualScreen(Minecraft client) throws Exception {
        Screen screen = client.screen;
        require(screen != null && screen.getClass().getMethod("registry").invoke(screen) == API.manual,
            "The actual registered manual Screen is not open");
        return screen;
    }

    // Observe existing public production methods only; no private state or implementation dependency.
    private static Object current() throws Exception { return API.manual.getClass().getMethod("current").invoke(API.manual); }
    private static String currentPath() throws Exception { return (String) current().getClass().getMethod("path").invoke(current()); }
    private static int currentOffset() throws Exception { return (Integer) current().getClass().getMethod("offset").invoke(current()); }
    private static List<String> lines(Iterable<String> content) {
        var result = new ArrayList<String>();
        if (content != null) content.forEach(result::add);
        return result;
    }
    private static void advance(int next) { phase = next; phaseFrame = frame; }
    private static String requiredProperty(String key) {
        String value = System.getProperty(key);
        require(value != null && !value.isBlank(), "Set " + key + " for this isolated client");
        return value;
    }

    private static void screenshot(Minecraft client, String name) throws Exception {
        Path target = report.resolveSibling(name);
        try (NativeImage pixels = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            pixels.writeToFile(target);
            results.add("SCREENSHOT " + target + " " + pixels.getWidth() + "x" + pixels.getHeight() + " actual frame=" + frame);
        }
    }

    private static void finish(Minecraft client, Throwable error) {
        if (finished) return;
        finished = true;
        boolean passed = error == null;
        if (!passed) {
            results.add("FAIL T03.client phase=" + phase + ": " + error);
            LOGGER.error("PLAYER_MANUAL_CLIENT_CONTRACT FAIL phase={}", phase, error);
        }
        List<String> reportLines = new ArrayList<>(List.of("status=" + (passed ? "PASS" : "FAIL"), "scenario=player-manual-client", "runId=" + runId));
        reportLines.addAll(results);
        try {
            Files.write(report, reportLines, StandardCharsets.UTF_8);
            for (String result : results) LOGGER.info("PLAYER_MANUAL_CLIENT_CONTRACT {}", result);
            LOGGER.info("PLAYER_MANUAL_CLIENT_CONTRACT_COMPLETE status={} report={}", passed ? "PASS" : "FAIL", report);
        } catch (Exception failure) { LOGGER.error("Could not write configured manual client report {}", report, failure); }
        finally { client.stop(); }
    }

    private static final class ProbeImage implements InteractiveImageRenderer {
        private float screenX, screenY, screenWidth, screenHeight;
        private int renders, clicks, clickedX, clickedY;
        private boolean consume = true;
        @Override public int getWidth() { return 64; }
        @Override public int getHeight() { return 20; }
        @Override public String getTooltip(String tooltip) { return "Actual SDK image clicks: " + clicks; }
        @Override public boolean onMouseClick(int x, int y) { clickedX = x; clickedY = y; clicks++; return consume; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY) {
            Vector3f origin = graphics.pose().last().pose().transformPosition(0, 0, 0, new Vector3f());
            Vector3f end = graphics.pose().last().pose().transformPosition(getWidth(), getHeight(), 0, new Vector3f());
            screenX = origin.x; screenY = origin.y; screenWidth = end.x - origin.x; screenHeight = end.y - origin.y; renders++;
            graphics.fill(0, 0, getWidth(), getHeight(), clicks == 0 ? 0xFF36B85A : 0xFF3689E0);
            graphics.drawString(Minecraft.getInstance().font, "clicks=" + clicks, 4, 6, 0xFFFFFFFF, false);
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
