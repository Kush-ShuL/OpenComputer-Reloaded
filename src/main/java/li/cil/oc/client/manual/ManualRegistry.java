package li.cil.oc.client.manual;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import li.cil.oc.OpenComputers;
import li.cil.oc.api.Items;
import li.cil.oc.api.detail.ItemInfo;
import li.cil.oc.api.detail.ManualAPI;
import li.cil.oc.api.manual.ContentProvider;
import li.cil.oc.api.manual.ImageProvider;
import li.cil.oc.api.manual.ImageRenderer;
import li.cil.oc.api.manual.PathProvider;
import li.cil.oc.api.manual.TabIconRenderer;
import li.cil.oc.api.prefab.TextureTabIconRenderer;
import li.cil.oc.client.gui.ManualScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Java port of the original client Manual registry. Construction never accesses game resources. */
public final class ManualRegistry implements ManualAPI {
    public static final String LANGUAGE_KEY = "%LANGUAGE%";
    public static final String HOME = LANGUAGE_KEY + "/index.md";
    public static final ManualRegistry INSTANCE = new ManualRegistry();

    public record Tab(TabIconRenderer renderer, String tooltip, String path) {}
    private record ImageRegistration(String prefix, ImageProvider provider) {}
    public static final class HistoryEntry {
        private final String path;
        private int offset;
        private HistoryEntry(String path) { this.path = path; }
        public String path() { return path; }
        public int offset() { return offset; }
        public void offset(int value) { offset = Math.max(0, value); }
    }

    private final List<Tab> tabs = new CopyOnWriteArrayList<>();
    private final List<PathProvider> pathProviders = new CopyOnWriteArrayList<>();
    private final List<ContentProvider> contentProviders = new CopyOnWriteArrayList<>();
    private final List<ImageRegistration> imageProviders = new CopyOnWriteArrayList<>();
    private final Deque<HistoryEntry> history = new ArrayDeque<>();

    private ManualRegistry() {
        history.push(new HistoryEntry(HOME));
        // Keep the builtin resource provider first, as promised by ResourceContentProvider's API.
        addProvider((ContentProvider) ManualRegistry::readResource);
        addProvider(new PathProvider() {
            @Override public String pathFor(ItemStack stack) { return definitionPath(Items.get(stack)); }
            @Override public String pathFor(Level world, BlockPos pos) {
                return world == null || pos == null ? null : pathFor(new ItemStack(world.getBlockState(pos).getBlock()));
            }
        });
        ManualImages.register(this);
        addTab(new TextureTabIconRenderer(texture("manual_home")), "oc:gui.Manual.Home", HOME);
        addTab(graphics -> renderTabItem("case1", graphics), "oc:gui.Manual.Blocks", LANGUAGE_KEY + "/block/index.md");
        addTab(graphics -> renderTabItem("cpu1", graphics), "oc:gui.Manual.Items", LANGUAGE_KEY + "/item/index.md");
    }

    private static void renderTabItem(String name, net.minecraft.client.gui.GuiGraphics graphics) {
        var info = Items.get(name);
        if (info != null) graphics.renderItem(info.createItemStack(1), 0, 0);
    }

    public static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "textures/gui/" + name + ".png");
    }

    private static String definitionPath(ItemInfo info) {
        if (info == null || List.of("debugger", "chipdiamond", "endstone").contains(info.name())) return null;
        return LANGUAGE_KEY + (info.block() == null ? "/item/" : "/block/") + info.name() + ".md";
    }

    @Override public void addTab(TabIconRenderer renderer, String tooltip, String path) {
        tabs.add(new Tab(Objects.requireNonNull(renderer, "renderer"), tooltip, normalize(Objects.requireNonNull(path, "path"))));
    }
    @Override public void addProvider(PathProvider provider) { pathProviders.add(Objects.requireNonNull(provider, "provider")); }
    @Override public void addProvider(ContentProvider provider) { contentProviders.add(Objects.requireNonNull(provider, "provider")); }
    @Override public void addProvider(String prefix, ImageProvider provider) {
        imageProviders.add(new ImageRegistration(prefix == null || prefix.isEmpty() ? "" : prefix + ":", Objects.requireNonNull(provider, "provider")));
    }
    public List<Tab> tabs() { return List.copyOf(tabs); }

    @Override public String pathFor(ItemStack stack) {
        for (var provider : pathProviders) {
            try { String result = provider.pathFor(stack); if (result != null) return result; }
            catch (Throwable error) { OpenComputers.LOGGER.warn("Manual item path provider failed", error); }
        }
        return null;
    }

    @Override public String pathFor(Level world, BlockPos pos) {
        for (var provider : pathProviders) {
            try { String result = provider.pathFor(world, pos); if (result != null) return result; }
            catch (Throwable error) { OpenComputers.LOGGER.warn("Manual block path provider failed", error); }
        }
        return null;
    }

    @Override public Iterable<String> contentFor(String path) {
        if (path == null) return null;
        String clean = normalize(path);
        String language = "en_us";
        try { language = Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(Locale.ROOT); }
        catch (RuntimeException error) { OpenComputers.LOGGER.debug("Manual language unavailable, using English", error); }
        List<String> result = contentWithRedirects(clean.replace(LANGUAGE_KEY, language), language);
        return result != null || language.equals("en_us") ? result : contentWithRedirects(clean.replace(LANGUAGE_KEY, "en_us"), "en_us");
    }

    private List<String> contentWithRedirects(String path, String language) {
        var seen = new LinkedHashSet<String>();
        while (seen.size() < 128) {
            path = normalize(path);
            if (!seen.add(path)) {
                var message = new ArrayList<String>();
                message.add("Redirection loop:"); message.addAll(seen); message.add(path);
                return message;
            }
            List<String> content = lookup(path);
            if (content == null || content.isEmpty()) return content;
            String first = content.getFirst();
            if (!first.regionMatches(true, 0, "#redirect ", 0, 10)) return content;
            path = makeRelative(first.substring(10).trim(), path).replace(LANGUAGE_KEY, language);
        }
        return List.of("Too many manual redirects.", path);
    }

    private List<String> lookup(String path) {
        for (ContentProvider provider : contentProviders) {
            try {
                Iterable<String> result = provider.getContent(path);
                if (result != null) {
                    var lines = new ArrayList<String>();
                    for (String line : result) lines.add(line == null ? "" : line);
                    return lines;
                }
            } catch (Throwable error) { OpenComputers.LOGGER.warn("Manual content provider failed for {}", path, error); }
        }
        return null;
    }

    private static Iterable<String> readResource(String path) {
        String resourcePath = "doc/" + path.replaceFirst("^/+", "").toLowerCase(Locale.ROOT);
        ResourceLocation location = ResourceLocation.tryBuild(OpenComputers.ID, resourcePath);
        if (location == null) return null;
        var resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty() && resourcePath.endsWith("/block/carpetedcapacitor.md")) {
            // Also repair direct links and a history entry created before this alias existed.
            // A pack's explicit variant page still wins over this shared-page fallback.
            resourcePath = resourcePath.substring(0, resourcePath.length() - "carpetedcapacitor.md".length()) + "capacitor.md";
            location = ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, resourcePath);
            resource = Minecraft.getInstance().getResourceManager().getResource(location);
        }
        if (resource.isEmpty()) return null;
        try (var reader = new BufferedReader(new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (Exception error) {
            OpenComputers.LOGGER.warn("Unable to read manual resource {}", location, error);
            return null;
        }
    }

    @Override public ImageRenderer imageFor(String href) {
        if (href == null) return null;
        // The last registered matching provider wins; a null result falls back to earlier providers.
        var providers = List.copyOf(imageProviders);
        for (int i = providers.size() - 1; i >= 0; i--) {
            var entry = providers.get(i);
            if (!href.startsWith(entry.prefix())) continue;
            try {
                var image = entry.provider().getImage(href.substring(entry.prefix().length()));
                if (image != null) return image;
            } catch (Throwable error) { OpenComputers.LOGGER.warn("Manual image provider failed for {}", href, error); }
        }
        return null;
    }

    @Override public void openFor(Player player) {
        if (player == null || !player.level().isClientSide()) return;
        execute(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == player) client.setScreen(new ManualScreen(this));
        });
    }

    @Override public void reset() {
        execute(() -> {
            synchronized (history) { history.clear(); history.push(new HistoryEntry(HOME)); }
            refreshScreen();
        });
    }

    @Override public void navigate(String path) {
        Objects.requireNonNull(path, "path");
        execute(() -> {
            String clean = normalize(path);
            synchronized (history) {
                if (!history.peek().path.equals(clean)) history.push(new HistoryEntry(clean));
            }
            refreshScreen();
        });
    }

    private static void execute(Runnable action) {
        Minecraft client = Minecraft.getInstance();
        if (client.isSameThread()) action.run(); else client.execute(action);
    }

    private void refreshScreen() {
        if (Minecraft.getInstance().screen instanceof ManualScreen screen && screen.registry() == this) screen.refreshPage();
    }

    public HistoryEntry current() { synchronized (history) { return history.peek(); } }
    public boolean back() {
        synchronized (history) {
            if (history.size() <= 1) return false;
            history.pop();
        }
        refreshScreen();
        return true;
    }

    public static String makeRelative(String path, String base) {
        if (path.startsWith("/")) return normalize(path);
        int slash = base.lastIndexOf('/');
        return normalize((slash < 0 ? "" : base.substring(0, slash + 1)) + path);
    }

    /** Resource paths use Unix separators even on a Windows client. */
    public static String normalize(String path) {
        boolean absolute = path.startsWith("/");
        var parts = new ArrayDeque<String>();
        for (String part : path.replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!parts.isEmpty() && !parts.peekLast().equals("..")) parts.removeLast();
                else if (!absolute) parts.addLast(part);
            } else parts.addLast(part);
        }
        return (absolute ? "/" : "") + String.join("/", parts);
    }
}
