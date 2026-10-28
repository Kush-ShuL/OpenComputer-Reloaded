package ocfixture;

import static ocfixture.MultiplayerProtocol.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Uses the ordinary login, remote ScreenGui and production payload handlers. */
public final class MultiplayerClientContract {
    private static final List<String> results = new ArrayList<>();
    private static MultiplayerProtocol.Step pending;
    private static int frames, stageFrame, disconnectAt, reconnectAt;
    private static long started;
    private static boolean connecting, finished, sent, acted, captured, primary;
    private static UUID token;
    private static Path directory, report;
    private static String clipboard;
    private MultiplayerClientContract() {}
    public static void register() {
        started = System.nanoTime();
        directory = Path.of(System.getProperty("ocfixture.multiplayerDirectory"));
        report = directory.resolve("client-results.txt");
        primary = System.getProperty("ocfixture.multiplayerPlayer").equals(MultiplayerServerContract.ONE);
        MultiplayerProtocol.receiver = packet -> { pending = packet; sent = false; acted = false; stageFrame = frames; };
        NeoForge.EVENT_BUS.addListener(MultiplayerClientContract::tick);
        NeoForge.EVENT_BUS.addListener(MultiplayerClientContract::rendered);
        MultiplayerFamiliesClient.register();
    }
    private static void connect(Minecraft client) {
        String address = System.getProperty("ocfixture.multiplayerAddress");
        ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address), new ServerData("Owned OC TCP fixture", address, ServerData.Type.OTHER), false, null);
        connecting = true;
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static Object action(String name) {
        return Enum.valueOf((Class) type("li.cil.oc.common.network.ScreenNetworking$InputType"), name);
    }
    private static boolean screen(Minecraft client) { return client.screen != null && client.screen.getClass().getName().equals("li.cil.oc.client.gui.ScreenGui"); }
    private static void input(Minecraft client, String action, int character, int code, String text) {
        call(client.screen, "send", action(action), character, code, 0d, 0d, 0, text);
    }
    private static void raw(UUID session, String action, int character, int code, String text) {
        PacketDistributor.sendToServer(payload("ScreenNetworking", "Input", MultiplayerServerContract.SCREEN, session, action(action), character, code, 0d, 0d, 0, text));
    }
    private static void ack(CompoundTag data) { PacketDistributor.sendToServer(new MultiplayerProtocol.Ack(pending.id(), data)); sent = true; }
    private static void ack() { ack(new CompoundTag()); }
    private static void tick(ClientTickEvent.Post event) {
        if (finished) return;
        Minecraft client = Minecraft.getInstance(); frames++;
        try {
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(12)) throw new AssertionError("Remote client timeout stage=" + (pending == null ? "login" : pending.id()) + " screen=" + client.screen);
            if (client.getOverlay() != null) return;
            if (disconnectAt > 0 && frames >= disconnectAt) {
                disconnectAt = 0; client.level.disconnect(); client.disconnect(new TitleScreen()); connecting = false; reconnectAt = frames + 60; pending = null; return;
            }
            if (client.level == null) {
                if (!connecting && client.screen instanceof TitleScreen && frames >= reconnectAt) connect(client);
                return;
            }
            require(!client.hasSingleplayerServer() && !client.getConnection().getConnection().isMemoryConnection(), "Remote test used an integrated or in-memory connection");
            MultiplayerFamiliesClient.observe(client);
            if (clipboard == null) clipboard = client.keyboardHandler.getClipboard();
            if (pending == null || sent) return;
            if (pending.id() >= MultiplayerProtocol.FAMILY_FIRST) { CompoundTag response = MultiplayerFamiliesClient.tick(client, pending, primary, results); if (response != null) ack(response); return; }
            switch (pending.id()) {
                case -1 -> throw new AssertionError(pending.data().getString("failure"));
                case 1 -> {
                    if (client.level.getBlockEntity(MultiplayerServerContract.SCREEN) == null || client.level.getBlockEntity(MultiplayerServerContract.COMPUTER) == null) return;
                    results.add("PASS actual dedicated-server login over a non-memory TCP connection as " + client.player.getGameProfile().getName()); ack();
                }
                case 2 -> { PacketDistributor.sendToServer(payload("ComputerNetworking", "Power", MultiplayerServerContract.COMPUTER, primary)); ack(); }
                case 3 -> {
                    if (!screen(client)) return;
                    token = (UUID) field(client.screen, "session");
                    input(client, "KEY_DOWN", primary ? 97 : 98, primary ? 30 : 48, "");
                    CompoundTag data = new CompoundTag(); data.putUUID("token", token); ack(data);
                }
                case 4 -> {
                    require(screen(client), "Production ScreenGui vanished before authenticated clipboard input");
                    if (primary) input(client, "CLIPBOARD", 0, 0, "valid TCP clipboard");
                    else raw(pending.data().getUUID("foreignToken"), "CLIPBOARD", 0, 0, "forged TCP clipboard");
                    ack();
                }
                case 5 -> {
                    if (!primary) { ack(); break; }
                    if (screen(client)) {
                        client.screen.onClose();
                        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(MultiplayerServerContract.COMPUTER), Direction.UP, MultiplayerServerContract.COMPUTER, false));
                        return;
                    }
                    if (client.screen instanceof AbstractContainerScreen<?>) { results.add("PASS real remote owner switched ScreenGui to the actual Case container"); ack(); }
                }
                case 6 -> { if (!primary) raw(token, "KEY_DOWN", 99, 46, ""); ack(); }
                case 7 -> {
                    if (!primary) { ack(); break; }
                    if (!screen(client)) return;
                    input(client, "KEY_DOWN", 120, 45, ""); ack(); disconnectAt = frames + 3;
                    results.add("PASS real remote client initiated normal disconnect with a held keyboard key");
                }
                case 8 -> {
                    if (!primary) { ack(); break; }
                    if (!screen(client)) return;
                    UUID current = (UUID) field(client.screen, "session");
                    require(!current.equals(token), "Real reconnect reused the old session token");
                    raw(pending.data().getUUID("oldToken"), "CLIPBOARD", 0, 0, "stale TCP session");
                    input(client, "CLIPBOARD", 0, 0, "fresh TCP session");
                    results.add("PASS real TCP reconnect opened a fresh production screen session"); ack();
                }
                case 9 -> {
                    UUID owner = pending.data().getUUID("owner");
                    if (!acted && primary) { PacketDistributor.sendToServer(payload("PetNetworking", "SetVisibility", false)); acted = true; }
                    if (client.level.getBlockEntity(MultiplayerServerContract.RAID) == null) return;
                    if (!((Set<?>) field(type("li.cil.oc.client.renderer.PetRenderer"), "HIDDEN")).contains(owner)) return;
                    results.add("PASS production pet visibility broadcast applied to this real remote renderer"); ack();
                }
                case 10 -> {
                    Object raid = client.level.getBlockEntity(MultiplayerServerContract.RAID); if (raid == null || ((byte[]) call(raid, "presenceData"))[0] != 1) return;
                    if (primary) {
                        if (client.screen == null || !client.screen.getClass().getName().endsWith("WaypointScreen")) return;
                        if (!client.keyboardHandler.getClipboard().equals("OC real TCP clipboard")) return;
                        PacketDistributor.sendToServer(payload("NetworkBlockNetworking", "WaypointLabel", client.level.dimension().location(), MultiplayerServerContract.WAYPOINT, "C19 TCP"));
                    }
                    results.add("PASS production remote RAID presence" + (primary ? ", waypoint screen/edit and debug clipboard" : " mirror")); ack();
                }
                case 11 -> { ack(); finish(client, null); }
                default -> throw new AssertionError("Unknown remote step " + pending.id());
            }
        } catch (Throwable error) { finish(client, error); }
    }
    private static void rendered(RenderFrameEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (finished || captured || client.level == null || !screen(client)) return;
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(directory.resolve("tcp-screen.png")); captured = true;
        } catch (Exception error) { finish(client, error); }
    }
    private static void finish(Minecraft client, Throwable error) {
        if (finished) return; finished = true;
        if (error != null) {
            results.add("FAIL remote client: " + error);
            if (client.getConnection() != null && pending != null) { CompoundTag data = new CompoundTag(); data.putString("failure", error.toString()); PacketDistributor.sendToServer(new MultiplayerProtocol.Ack(pending.id(), data)); }
        }
        try {
            List<String> lines = new ArrayList<>(List.of("status=" + (error == null ? "PASS" : "FAIL"), "scenario=real-tcp-client", "player=" + System.getProperty("ocfixture.multiplayerPlayer"))); lines.addAll(results);
            Files.write(report, lines, StandardCharsets.UTF_8);
            if (clipboard != null) client.keyboardHandler.setClipboard(clipboard);
            if (client.level != null) { client.level.disconnect(); client.disconnect(new TitleScreen()); }
        } catch (Exception failure) { throw new IllegalStateException(failure); }
        finally { client.stop(); }
    }
}
