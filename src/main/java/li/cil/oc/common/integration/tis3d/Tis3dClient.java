package li.cil.oc.common.integration.tis3d;

import li.cil.manual.api.ManualModel;
import li.cil.manual.api.prefab.provider.NamespaceDocumentProvider;
import li.cil.manual.api.provider.DocumentProvider;
import li.cil.manual.api.util.Constants;
import li.cil.manual.api.util.MatchResult;
import li.cil.oc.OpenComputers;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Client-only replacement of the original TIS-3D ResourceContentProvider registration. */
@OnlyIn(Dist.CLIENT)
final class Tis3dClient {
    private static final DeferredRegister<DocumentProvider> DOCUMENTS = DeferredRegister.create(Constants.DOCUMENT_PROVIDER_REGISTRY, OpenComputers.ID);
    static { DOCUMENTS.register("tis3d_adapter", AdapterDocumentation::new); }
    private Tis3dClient() {}
    static void register(IEventBus modBus) { DOCUMENTS.register(modBus); }

    private static final class AdapterDocumentation extends NamespaceDocumentProvider {
        private static final ResourceLocation TIS_MANUAL = ResourceLocation.fromNamespaceAndPath("tis3d", "manual");
        AdapterDocumentation() { super(OpenComputers.ID, "doc/tis3d"); }

        @Override public MatchResult matches(ManualModel manual) {
            var registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registryOrThrow(Constants.MANUAL_REGISTRY);
            return TIS_MANUAL.equals(registry.getKey(manual)) ? MatchResult.MATCH : MatchResult.MISMATCH;
        }
    }
}
