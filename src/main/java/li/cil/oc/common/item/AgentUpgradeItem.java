package li.cil.oc.common.item;

import li.cil.oc.api.internal.Tiered;
import net.minecraft.world.item.Item;

/** The original inventory upgrade and real hot-swap slot containers. */
public final class AgentUpgradeItem extends Item implements Tiered {
    public final String kind;
    private final int tier;
    public AgentUpgradeItem(Properties properties,String kind,int tier){super(properties);this.kind=kind;this.tier=tier;}
    @Override public int tier(){return tier;}
}
