package li.cil.oc.client.integration.jade;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Discovered by Jade; common OC startup must not link this optional API entrypoint. */
@WailaPlugin("jade")
public final class OpenComputersJadePlugin implements IWailaPlugin {
    static final ResourceLocation INFORMATION = id("information");
    static final ResourceLocation ADDRESS = id("address");
    static final ResourceLocation ENERGY = id("energy");
    static final ResourceLocation COMPONENT_NAME = id("component_name");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("opencomputers", path);
    }

    @Override public void register(IWailaCommonRegistration registration) {
        if (!JadeIntegration.enabled()) return;
        registration.registerBlockDataProvider(BlockDataProvider.INSTANCE, BlockEntity.class);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        if (!JadeIntegration.enabled()) return;
        // The three original Waila switches are independent of the machine-specific lines.
        registration.addConfig(ADDRESS, true);
        registration.addConfig(ENERGY, true);
        registration.addConfig(COMPONENT_NAME, true);
        registration.registerBlockComponent(BlockTooltipProvider.INSTANCE, Block.class);
    }
}
