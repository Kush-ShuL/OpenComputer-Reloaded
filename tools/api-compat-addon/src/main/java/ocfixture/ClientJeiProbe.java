package ocfixture;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/** Observes the actual JEI runtime; it registers no substitute recipes or categories. */
@JeiPlugin
public final class ClientJeiProbe implements IModPlugin {
    public static IJeiRuntime runtime;
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("oc_api_compat","client_acceptance");}
    @Override public void onRuntimeAvailable(IJeiRuntime value){runtime=value;}
    @Override public void onRuntimeUnavailable(){runtime=null;}
}
