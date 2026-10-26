package li.cil.oc.common.item;

import li.cil.oc.api.internal.Tiered;
import net.minecraft.world.item.Item;

/** Empty assembly housing, distinct from the assembled drone and its internal upgrades. */
public final class AgentCaseItem extends Item implements Tiered {
    private final int tier;
    public AgentCaseItem(Properties properties,int tier){super(properties);this.tier=tier;}
    @Override public int tier(){return tier;}
}
