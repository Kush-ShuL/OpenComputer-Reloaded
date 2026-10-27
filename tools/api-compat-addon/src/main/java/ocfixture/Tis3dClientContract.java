package ocfixture;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import li.cil.manual.api.ManualModel;
import li.cil.manual.api.content.Document;
import li.cil.manual.api.provider.DocumentProvider;
import li.cil.manual.api.util.Constants;
import li.cil.manual.api.util.MatchResult;
import li.cil.tis3d.api.serial.SerialInterfaceProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Public client manual APIs against the actual registered OC/TIS-3D providers. */
@OnlyIn(Dist.CLIENT)
public final class Tis3dClientContract {
    private static final Logger LOGGER = LoggerFactory.getLogger("oc-tis3d-client-contract");
    private static final ResourceLocation DOCUMENTS = ResourceLocation.fromNamespaceAndPath("opencomputers", "tis3d_adapter");
    private static final ResourceLocation MANUAL = ResourceLocation.fromNamespaceAndPath("tis3d", "manual");
    private static final ResourceLocation SERIAL = ResourceLocation.fromNamespaceAndPath("opencomputers", "adapter");
    private static final List<String> LANGUAGES = List.of("en_us", "zh_cn", "ru_ru");
    private static Path report;
    private static long started;
    private static boolean registered, finished;

    private Tis3dClientContract() {}

    /** Only the isolated physical-client test branch may call this entrypoint. */
    public static void register() {
        if (registered) return;
        String target = System.getProperty("ocfixture.tis3dClientReport");
        require(target != null && !target.isBlank(), "Set the isolated client's ocfixture.tis3dClientReport path");
        report = Path.of(target);
        require(report.isAbsolute(), "The isolated client report path must be absolute");
        registered = true; started = System.nanoTime();
        NeoForge.EVENT_BUS.addListener(Tis3dClientContract::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        if (finished) return;
        Minecraft client = Minecraft.getInstance();
        boolean ready = client.getOverlay() == null && client.screen instanceof TitleScreen;
        if (!ready && System.nanoTime() - started < TimeUnit.MINUTES.toNanos(3)) return;
        finished = true;
        List<String> results = new ArrayList<>();
        boolean passed = false;
        try {
            require(ready, "Client never reached a resource-loaded TitleScreen; screen="
                + (client.screen == null ? "null" : client.screen.getClass().getName())
                + ", overlay=" + (client.getOverlay() == null ? "null" : client.getOverlay().getClass().getName()));
            verify(client, results);
            passed = true;
        } catch (Throwable error) {
            results.add("FAIL I17.client: " + error);
            LOGGER.error("TIS3D_CLIENT_CONTRACT FAIL", error);
        }
        List<String> lines = new ArrayList<>();
        lines.add("status=" + (passed ? "PASS" : "FAIL"));
        lines.add("scenario=tis3d-client-manual");
        lines.addAll(results);
        try {
            // The shared run setup owns the report directory. Do not create directories.
            Files.write(report, lines, StandardCharsets.UTF_8);
            for (String result : results) LOGGER.info("TIS3D_CLIENT_CONTRACT {}", result);
            LOGGER.info("TIS3D_CLIENT_CONTRACT_COMPLETE status={} report={}", passed ? "PASS" : "FAIL", report);
        } catch (Exception error) {
            LOGGER.error("TIS3D_CLIENT_CONTRACT could not write its configured report {}", report, error);
        } finally {
            // Normal shutdown affects only this isolated client's own Minecraft instance.
            client.stop();
        }
    }

    private static void verify(Minecraft client, List<String> results) throws Exception {
        RegistryAccess access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var providers = access.registryOrThrow(Constants.DOCUMENT_PROVIDER_REGISTRY);
        var manuals = access.registryOrThrow(Constants.MANUAL_REGISTRY);
        DocumentProvider provider = providers.get(DOCUMENTS);
        ManualModel manual = manuals.get(MANUAL);
        require(provider != null && DOCUMENTS.equals(providers.getKey(provider)), "Actual MarkdownManual registry has no OC TIS-3D document provider");
        require(manual != null && MANUAL.equals(manuals.getKey(manual)), "Actual MarkdownManual registry has no TIS-3D manual model");
        require(provider.matches(manual) == MatchResult.MATCH, "OC document provider does not explicitly MATCH the real TIS-3D manual");
        results.add("PASS I17.client.registry real " + DOCUMENTS + " provider=" + provider.getClass().getName() + " MATCH " + MANUAL);

        var serialProviders = access.registryOrThrow(SerialInterfaceProvider.REGISTRY);
        SerialInterfaceProvider serial = serialProviders.get(SERIAL);
        require(serial != null, "Actual TIS-3D serial registry has no OC Adapter provider");
        var reference = serial.getDocumentationReference().orElseThrow(() -> new AssertionError("Actual OC Adapter has no protocol documentation reference"));
        String link = reference.getLink();
        require("opencomputersadapter.md".equals(link), "Actual serial protocol link is not relative to the localized protocols index: " + link);

        Map<ResourceLocation, String> contents = new LinkedHashMap<>();
        for (String language : LANGUAGES) {
            String path = language + "/protocols/" + link;
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("opencomputers", "doc/tis3d/" + path);
            var resource = client.getResourceManager().getResource(location).orElseThrow(() -> new AssertionError("Client did not load protocol resource " + location));
            String expected;
            try (var reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                expected = String.join("\n", reader.lines().toList()).strip();
            }
            require(!expected.isBlank(), "Actual protocol resource is empty: " + location);
            // NamespaceDocumentProvider builds basePath + its FIRST argument.
            Document direct = provider.getDocument(path, language).orElseThrow(() -> new AssertionError("Registered provider cannot read " + path));
            Document routed = manual.documentFor(path).orElseThrow(() -> new AssertionError("Actual manual cannot route " + path));
            document(direct, location, expected, "provider");
            document(routed, location, expected, "manual.documentFor");
            contents.put(location, expected);
            results.add("PASS I17.client.language " + language + " provider/manual read " + location + " lines=" + routed.getLines().size());
        }

        String indexPath = ManualModel.LANGUAGE_KEY + "/protocols/index.md";
        Document index = manual.documentFor(indexPath).orElseThrow(() -> new AssertionError("Actual TIS-3D protocol index is unavailable"));
        require(index.getLines().stream().anyMatch(line -> line.contains("](" + link + ")")),
            "Actual generated player-facing protocol index does not contain OC Adapter's registered link");
        manual.reset();
        manual.push(indexPath);
        String resolved = manual.resolve(link);
        require((ManualModel.LANGUAGE_KEY + "/protocols/" + link).equals(resolved), "Real manual resolved a duplicated or incorrect protocol path: " + resolved);
        Document clicked = manual.documentFor(resolved).orElseThrow(() -> new AssertionError("Actual player protocol link does not open its document: " + resolved));
        String expected = contents.get(clicked.getLocation());
        require(expected != null, "Player protocol link selected an unrelated document: " + clicked.getLocation());
        document(clicked, clicked.getLocation(), expected, "player link");
        String selected = client.getLanguageManager().getSelected();
        if (LANGUAGES.contains(selected)) require(clicked.getLocation().getPath().equals("doc/tis3d/" + selected + "/protocols/" + link), "Player protocol link ignored the selected language");
        results.add("PASS I17.client.link actual generated index -> " + link + " -> manual.resolve=" + resolved
            + " -> manual.documentFor=" + clicked.getLocation() + " selectedLanguage=" + selected);
        results.add("LIMIT I17.client: public registry/document routing was exercised after real resource loading; rendered GUI/images and remote multiplayer are not claimed");
    }

    private static void document(Document document, ResourceLocation location, String expected, String source) {
        require(location.equals(document.getLocation()), source + " returned the wrong protocol resource: " + document.getLocation());
        require(expected.equals(String.join("\n", document.getLines()).strip()), source + " content differs from actual loaded protocol resource " + location);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
