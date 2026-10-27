package li.cil.oc.client.integration.jade;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Client tooltip formatting only; network state always comes from the server provider. */
final class BlockTooltipProvider implements IBlockComponentProvider {
    static final BlockTooltipProvider INSTANCE = new BlockTooltipProvider();

    private BlockTooltipProvider() {}

    @Override public ResourceLocation getUid() { return OpenComputersJadePlugin.INFORMATION; }
    @Override public boolean isRequired() { return true; }

    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!JadeIntegration.enabled() || !BlockDataProvider.isOcBlock(accessor)) return;
        CompoundTag data = accessor.getServerData().getCompound(BlockDataProvider.DATA_KEY);
        if (data.isEmpty()) return;

        if (data.contains("signalStrength", Tag.TAG_ANY_NUMERIC)) {
            var addresses = data.getList("addresses", Tag.TAG_STRING);
            int side = accessor.getSide().get3DDataValue();
            if (config.get(OpenComputersJadePlugin.ADDRESS) && side < addresses.size()) {
                String address = addresses.getString(side);
                if (!address.isEmpty()) tooltip.add(Component.translatable("oc:gui.Analyzer.Address", address));
            }
            tooltip.add(Component.translatable("oc:gui.Analyzer.WirelessStrength", Integer.toString((int) data.getDouble("signalStrength"))));
        } else if (data.contains("progress", Tag.TAG_ANY_NUMERIC)) {
            tooltip.add(Component.translatable("oc:gui.Assembler.Progress", Integer.toString((int) data.getDouble("progress")), formatTime(data.getInt("timeRemaining"))));
            if (data.contains("outputName", Tag.TAG_STRING) && !data.getString("outputName").isEmpty()) {
                try {
                    Component outputName = Component.Serializer.fromJson(data.getString("outputName"), accessor.getLevel().registryAccess());
                    if (outputName != null && !outputName.getString().isEmpty()) {
                        tooltip.add(Component.translatable("oc:gui.Assembler.Building", outputName));
                    }
                } catch (com.google.gson.JsonParseException ignored) {
                    // Malformed/mismatched server data must not create a fabricated output name.
                }
            }
        } else if (data.contains("chargeSpeed", Tag.TAG_ANY_NUMERIC)) {
            tooltip.add(Component.translatable("oc:gui.Analyzer.ChargerSpeed", (int) (data.getDouble("chargeSpeed") * 100) + "%"));
        }

        if (data.contains("nodes", Tag.TAG_LIST)) {
            var nodes = data.getList("nodes", Tag.TAG_COMPOUND);
            int side = accessor.getSide().get3DDataValue();
            if (side < nodes.size()) appendNode(tooltip, nodes.getCompound(side), config);
        } else {
            appendNode(tooltip, data, config);
        }
    }

    private static void appendNode(ITooltip tooltip, CompoundTag node, IPluginConfig config) {
        String address = node.getString("address");
        if (config.get(OpenComputersJadePlugin.ADDRESS) && !address.isEmpty()) {
            tooltip.add(Component.translatable("oc:gui.Analyzer.Address", address));
        }
        if (config.get(OpenComputersJadePlugin.ENERGY) && node.contains("buffer", Tag.TAG_ANY_NUMERIC)
                && node.getInt("bufferSize") > 0) {
            tooltip.add(Component.translatable("oc:gui.Analyzer.StoredEnergy", node.getInt("buffer") + "/" + node.getInt("bufferSize")));
        }
        String name = node.getString("componentName");
        if (config.get(OpenComputersJadePlugin.COMPONENT_NAME) && !name.isEmpty()) {
            tooltip.add(Component.translatable("oc:gui.Analyzer.ComponentName", name));
        }
    }

    private static String formatTime(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
