package li.cil.oc.common.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.util.ThreadPoolFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.apache.maven.artifact.versioning.ComparableVersion;

/** Login-triggered background check, derived from OC UpdateCheck.scala (MIT).
 * Release fields and conditional GETs follow the public GitHub REST API.
 * No request is issued until an eligible player joins with updateCheck enabled.
 */
public final class UpdateChecker {
    private static final String REPOSITORY = "Kush-ShuL/OpenComputer-Reloaded";
    private static final URI ENDPOINT = URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases?per_page=100");
    private static final Pattern STABLE_VERSION = Pattern.compile("\\d+(?:\\.\\d+){1,3}");
    private static final Pattern MINECRAFT_VERSION = Pattern.compile("(?<![0-9])1\\.(?:1[0-9]|2[0-9])(?:\\.[0-9]+)?(?![0-9])");
    private static final long CACHE_MILLIS = Duration.ofHours(24).toMillis();
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_PAGES = 5;
    private static CompletableFuture<Optional<Release>> result;
    private static boolean initialized;
    private UpdateChecker() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.register(UpdateChecker.class);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Settings.get().updateCheck || !(event.getEntity() instanceof ServerPlayer player)
                || player instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        MinecraftServer server = player.server;
        if (!eligible(server, player)) return;
        String local = ModList.get().getModContainerById(OpenComputers.ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse(OpenComputers.VERSION);
        if (local.contains("@VERSION@")) return;
        check(server.getProxy()).thenAccept(release -> {
            if (release.isEmpty() || !Settings.get().updateCheck) return;
            Release available = release.get();
            if (new ComparableVersion(available.version).compareTo(new ComparableVersion(local)) <= 0) return;
            // HTTP completion is off-thread; all player/world access is scheduled on the owning server.
            server.execute(() -> {
                if (!Settings.get().updateCheck || server.getPlayerList().getPlayer(player.getUUID()) != player
                        || !eligible(server, player)) return;
                Component version = Component.literal(available.tag).withStyle(style -> style
                        .withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, available.url)));
                player.sendSystemMessage(Component.literal("[" + OpenComputers.NAME + "] ")
                        .append(Component.translatable("oc:gui.Chat.NewVersion", version)));
            });
        });
    }

    private static boolean eligible(MinecraftServer server, ServerPlayer player) {
        return !server.isDedicatedServer() || server.getPlayerList().isOp(player.getGameProfile());
    }

    private static synchronized CompletableFuture<Optional<Release>> check(Proxy proxy) {
        if (!Settings.get().updateCheck) return CompletableFuture.completedFuture(Optional.empty());
        if (result != null) return result; // Once per process, including failed/rate-limited requests.
        result = new CompletableFuture<>();
        CompletableFuture<Optional<Release>> promise = result;
        ThreadPoolFactory.daemon(() -> {
            try { promise.complete(fetch(proxy)); }
            catch (Exception error) {
                OpenComputers.LOGGER.warn("Update check for {} failed: {}", OpenComputers.NAME, error.toString());
                promise.complete(Optional.empty());
            }
        }, "OpenComputers-UpdateCheck").start();
        return result;
    }

    private record Release(String tag, String version, String url) {}
    private record Cache(long checkedAt, String etag, Optional<Release> release) {}
    private record Response(int status, String etag, String links, byte[] body) {}

    private static Optional<Release> fetch(Proxy proxy) throws IOException {
        if (!Settings.get().updateCheck) return Optional.empty();
        Path file = FMLPaths.GAMEDIR.get().resolve("cache/opencomputers/update-check.json");
        Cache cache = readCache(file);
        long now = System.currentTimeMillis();
        if (cache != null && now >= cache.checkedAt && now - cache.checkedAt < CACHE_MILLIS) return cache.release;
        OpenComputers.LOGGER.info("Checking {} for a newer Minecraft 1.21.1 NeoForge release", REPOSITORY);
        Release latest = null;
        URI page = ENDPOINT;
        String etag = "";
        for (int index = 0; page != null && index < MAX_PAGES; index++) {
            if (!Settings.get().updateCheck) return Optional.empty();
            Response response = request(page, proxy, index == 0 && cache != null ? cache.etag : "");
            if (response.status == HttpURLConnection.HTTP_NOT_MODIFIED && cache != null && index == 0) {
                writeCache(file, new Cache(now, cache.etag, cache.release));
                return cache.release;
            }
            if (response.status != HttpURLConnection.HTTP_OK)
                throw new IOException("GitHub releases returned HTTP " + response.status + "; no retry until the next process");
            if (index == 0) etag = response.etag;
            JsonArray releases;
            try { releases = JsonParser.parseString(new String(response.body, StandardCharsets.UTF_8)).getAsJsonArray(); }
            catch (RuntimeException error) { throw new IOException("Invalid GitHub releases response", error); }
            for (JsonElement element : releases) {
                Release candidate = candidate(element);
                if (candidate != null && (latest == null || new ComparableVersion(candidate.version)
                        .compareTo(new ComparableVersion(latest.version)) > 0)) latest = candidate;
            }
            page = nextPage(response.links);
        }
        Optional<Release> available = Optional.ofNullable(latest);
        writeCache(file, new Cache(now, etag, available));
        return available;
    }

    /** A stable tag alone is insufficient: the release must actually contain the playable mod. */
    private static Release candidate(JsonElement element) {
        try {
            JsonObject release = element.getAsJsonObject();
            if (!release.has("draft") || release.get("draft").getAsBoolean()
                    || !release.has("prerelease") || release.get("prerelease").getAsBoolean()) return null;
            String tag = release.get("tag_name").getAsString();
            String version = tag;
            if (tag.contains("/")) {
                String[] parts = tag.split("/", 2);
                if (!parts[0].equals("1.21.1") && !parts[0].equalsIgnoreCase("mc1.21.1")) return null;
                version = parts[1];
            }
            if (version.startsWith("v") || version.startsWith("V")) version = version.substring(1);
            // Excludes api-*, alpha, beta, RC and snapshots even if a publisher omitted prerelease=true.
            if (!STABLE_VERSION.matcher(version).matches()) return null;
            boolean fullMod = false;
            for (JsonElement assetElement : release.getAsJsonArray("assets")) {
                JsonObject asset = assetElement.getAsJsonObject();
                String name = asset.get("name").getAsString().toLowerCase(Locale.ROOT);
                if (!(name.startsWith("opencomputer-reloaded-") || name.startsWith("opencomputers-"))
                        || !name.endsWith(".jar") || !asset.get("state").getAsString().equals("uploaded")
                        || asset.get("size").getAsLong() <= 0
                        || name.matches(".*(?:^|[-_.])(?:api|sources?|javadoc|dev|tests?)(?:[-_.]|$).*")
                        || name.contains("fabric") || name.contains("quilt")) continue;
                // The mod's own semantic version may also start with 1.x; do not mistake it for Minecraft.
                var minecraft = MINECRAFT_VERSION.matcher(name.replace(version.toLowerCase(Locale.ROOT), ""));
                if (minecraft.find() && !minecraft.group().equals("1.21.1")) continue;
                URI download = URI.create(asset.get("browser_download_url").getAsString());
                if (!"https".equalsIgnoreCase(download.getScheme()) || !"github.com".equalsIgnoreCase(download.getHost())) continue;
                fullMod = true; break;
            }
            String url = release.get("html_url").getAsString();
            URI link = URI.create(url);
            if (!fullMod || !"https".equalsIgnoreCase(link.getScheme()) || !"github.com".equalsIgnoreCase(link.getHost())) return null;
            return new Release(tag, version, url);
        } catch (RuntimeException error) { return null; } // Ignore one malformed/different-kind release, not the whole listing.
    }

    private static Response request(URI uri, Proxy proxy, String etag) throws IOException {
        if (!Settings.get().updateCheck) throw new IOException("Update checks were disabled");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection(proxy == null ? Proxy.NO_PROXY : proxy);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
        connection.setRequestProperty("User-Agent", OpenComputers.NAME + "/" + OpenComputers.VERSION);
        if (etag != null && !etag.isBlank()) connection.setRequestProperty("If-None-Match", etag);
        try {
            int status = connection.getResponseCode();
            byte[] body = new byte[0];
            if (status == HttpURLConnection.HTTP_OK) {
                long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    for (int count; (count = input.read(buffer)) >= 0;) {
                        if (!Settings.get().updateCheck) throw new IOException("Update checks were disabled");
                        if (System.nanoTime() > deadline) throw new IOException("GitHub release response timed out");
                        if (output.size() + count > MAX_RESPONSE_BYTES) throw new IOException("GitHub release response is too large");
                        output.write(buffer, 0, count);
                    }
                    body = output.toByteArray();
                }
            }
            return new Response(status, connection.getHeaderField("ETag"), connection.getHeaderField("Link"), body);
        } finally { connection.disconnect(); }
    }

    private static URI nextPage(String links) throws IOException {
        if (links == null) return null;
        for (String link : links.split(",")) {
            if (!link.contains("rel=\"next\"")) continue;
            int start = link.indexOf('<'), end = link.indexOf('>');
            if (start < 0 || end <= start) throw new IOException("Invalid GitHub pagination link");
            URI next = URI.create(link.substring(start + 1, end));
            if (!next.getScheme().equals("https") || !next.getHost().equals("api.github.com")
                    || !next.getPath().equals(ENDPOINT.getPath())) throw new IOException("Unexpected GitHub pagination target");
            return next;
        }
        return null;
    }

    private static Cache readCache(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 65536) return null;
            JsonObject object = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (object.get("schema").getAsInt() != 1 || !object.get("repository").getAsString().equals(REPOSITORY)) return null;
            Optional<Release> release = Optional.empty();
            if (!object.get("release").isJsonNull()) {
                JsonObject entry = object.getAsJsonObject("release");
                String version = entry.get("version").getAsString(), url = entry.get("url").getAsString();
                if (!STABLE_VERSION.matcher(version).matches() || !url.startsWith("https://github.com/" + REPOSITORY + "/releases/")) return null;
                release = Optional.of(new Release(entry.get("tag").getAsString(), version, url));
            }
            return new Cache(object.get("checkedAt").getAsLong(), object.has("etag") ? object.get("etag").getAsString() : "", release);
        } catch (Exception ignored) { return null; }
    }

    private static void writeCache(Path file, Cache cache) {
        Path temporary = null;
        try {
            JsonObject object = new JsonObject(); object.addProperty("schema", 1);
            object.addProperty("repository", REPOSITORY); object.addProperty("checkedAt", cache.checkedAt);
            if (cache.etag != null) object.addProperty("etag", cache.etag);
            JsonElement release = JsonNull.INSTANCE;
            if (cache.release.isPresent()) {
                Release value = cache.release.get(); JsonObject entry = new JsonObject();
                entry.addProperty("tag", value.tag); entry.addProperty("version", value.version); entry.addProperty("url", value.url); release = entry;
            }
            object.add("release", release);
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "update-check-", ".tmp");
            Files.writeString(temporary, object.toString(), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) { OpenComputers.LOGGER.debug("Could not save update-check cache: {}", error.toString()); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {} }
    }
}
