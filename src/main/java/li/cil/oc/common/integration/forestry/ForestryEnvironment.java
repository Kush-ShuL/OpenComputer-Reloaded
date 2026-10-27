package li.cil.oc.common.integration.forestry;

import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The real neighboring block remains owned by the Adapter's normal environment lifecycle. */
abstract class ForestryEnvironment<T extends BlockEntity> extends AbstractManagedEnvironment implements NamedBlock {
    private final T tile;
    private final String name;

    ForestryEnvironment(T tile, String name) {
        this.tile = tile;
        this.name = name;
        setNode(Network.newNode(this, Visibility.Network).withComponent(name).create());
    }

    final T target() {
        if (tile.isRemoved()) throw new IllegalStateException(name + " is no longer present");
        return tile;
    }

    @Override public final String preferredName() { return name; }
    @Override public final int priority() { return 0; }
}
