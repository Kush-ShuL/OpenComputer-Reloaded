package li.cil.oc.server.component;

import li.cil.oc.api.network.*;
import net.minecraft.nbt.*;

public final class UpgradeBarcodeReader extends UpgradeBase {
    public UpgradeBarcodeReader(EnvironmentHost host) {
        super(host, "barcode_reader", "Barcode reader upgrade", "Readerizer Deluxe", true);
        ((Component) node()).setVisibility(Visibility.Network);
    }

    @Override public void onMessage(Message message) {
        super.onMessage(message);
        TabletUseData use = TabletUseData.from(message);
        if (use == null) return;

        var entity = host.world().getBlockEntity(use.position());
        Node[] nodes;
        if (entity instanceof Analyzable analyzable) {
            nodes = analyzable.onAnalyze(use.player(), use.side(), use.hitX(), use.hitY(), use.hitZ());
        } else if (entity instanceof SidedEnvironment sided) {
            nodes = new Node[]{sided.sidedNode(use.side())};
        } else if (entity instanceof Environment environment) {
            nodes = new Node[]{environment.node()};
        } else return;
        if (nodes == null) return;

        ListTag analyzed = new ListTag();
        for (Node node : nodes) {
            if (node == null) continue;
            CompoundTag value = new CompoundTag();
            if (node instanceof Component component) value.putString("type", component.name());
            String address = node.address();
            if (address != null && !address.isEmpty()) value.putString("address", address);
            analyzed.add(value);
        }
        use.result().put("analyzed", analyzed);
    }
}
