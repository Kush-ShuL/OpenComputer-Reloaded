package li.cil.oc.client.integration.jade;

import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.common.block.entity.AssemblerBlockEntity;
import li.cil.oc.common.block.entity.CableBlockEntity;
import li.cil.oc.common.block.entity.ChargerBlockEntity;
import li.cil.oc.common.block.entity.DiskDriveBlockEntity;
import li.cil.oc.common.block.entity.HologramBlockEntity;
import li.cil.oc.common.block.entity.KeyboardBlockEntity;
import li.cil.oc.common.block.entity.PowerConverterBlockEntity;
import li.cil.oc.common.block.entity.PowerDistributorBlockEntity;
import li.cil.oc.common.block.entity.RackBlockEntity;
import li.cil.oc.common.block.entity.RelayBlockEntity;
import li.cil.oc.common.block.entity.ScreenBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/** Server-side Waila data migration; this class contains no client-only types. */
final class BlockDataProvider implements IServerDataProvider<BlockAccessor> {
    static final BlockDataProvider INSTANCE = new BlockDataProvider();
    static final String DATA_KEY = "opencomputers:waila";

    private BlockDataProvider() {}

    @Override public ResourceLocation getUid() { return OpenComputersJadePlugin.INFORMATION; }

    static boolean isOcBlock(BlockAccessor accessor) {
        return "opencomputers".equals(BuiltInRegistries.BLOCK.getKey(accessor.getBlock()).getNamespace());
    }

    @Override public boolean shouldRequestData(BlockAccessor accessor) {
        return JadeIntegration.enabled() && isOcBlock(accessor) && accessor.getBlockEntity() != null;
    }

    @Override public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
        if (!JadeIntegration.enabled() || !isOcBlock(accessor)) return;
        BlockEntity tile = accessor.getBlockEntity();
        if (tile == null || tile.isRemoved() || accessor.getLevel().isClientSide) return;

        CompoundTag data = new CompoundTag();
        // These are exactly the original NotAnalyzable implementations. Do not expose their nodes.
        boolean analyzable = !(tile instanceof CableBlockEntity || tile instanceof PowerConverterBlockEntity
                || tile instanceof PowerDistributorBlockEntity);
        if (analyzable) {
            if (tile instanceof SidedEnvironment sided) {
                ListTag nodes = new ListTag();
                for (Direction side : Direction.values()) nodes.add(writeNode(sided.sidedNode(side)));
                data.put("nodes", nodes);
            } else if (tile instanceof Environment environment) {
                data.merge(writeNode(environment.node()));
            }
        }

        if (tile instanceof RelayBlockEntity relay) {
            // The relay update packet is a scalar strength snapshot, not a full machine save.
            data.putDouble("signalStrength", relay.getUpdateTag(accessor.getLevel().registryAccess()).getDouble("oc:strength"));
            ListTag addresses = new ListTag();
            for (String address : relay.componentAddresses()) addresses.add(StringTag.valueOf(address));
            data.put("addresses", addresses);
        } else if (tile instanceof AssemblerBlockEntity assembler) {
            ignoreSidedness(data, assembler.node());
            if (assembler.busy()) {
                data.putDouble("progress", assembler.progress());
                data.putInt("timeRemaining", assembler.timeRemaining());
                var output = assembler.pendingOutput();
                if (!output.isEmpty()) data.putString("outputName", net.minecraft.network.chat.Component.Serializer.toJson(
                        output.getHoverName(), accessor.getLevel().registryAccess()));
            }
        } else if (tile instanceof ChargerBlockEntity charger) {
            data.putDouble("chargeSpeed", charger.chargeSpeed);
        } else if (tile instanceof DiskDriveBlockEntity drive) {
            data.remove("address");
            data.merge(writeNode(drive.filesystemNode()));
        } else if (tile instanceof HologramBlockEntity hologram) {
            ignoreSidedness(data, hologram.node());
        } else if (tile instanceof KeyboardBlockEntity keyboard) {
            ignoreSidedness(data, keyboard.node());
        } else if (tile instanceof ScreenBlockEntity screen) {
            ignoreSidedness(data, screen.node());
        } else if (tile instanceof RackBlockEntity) {
            // The original Waila rack implementation is TODO, including its per-side nodes.
            data.remove("nodes");
        }
        if (!data.isEmpty()) serverData.put(DATA_KEY, data);
    }

    private static CompoundTag writeNode(Node node) {
        CompoundTag data = new CompoundTag();
        if (node == null || node.reachability() == Visibility.None) return data;
        if (node.address() != null) data.putString("address", node.address());
        if (node instanceof Connector connector) {
            data.putInt("buffer", (int) connector.localBuffer());
            data.putInt("bufferSize", (int) connector.localBufferSize());
        }
        if (node instanceof Component component && component.name() != null) data.putString("componentName", component.name());
        return data;
    }

    private static void ignoreSidedness(CompoundTag data, Node node) {
        ListTag nodes = new ListTag();
        CompoundTag nodeData = writeNode(node);
        for (Direction ignored : Direction.values()) nodes.add(nodeData.copy());
        data.put("nodes", nodes);
    }
}
