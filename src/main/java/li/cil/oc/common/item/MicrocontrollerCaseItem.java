package li.cil.oc.common.item;
import net.minecraft.world.item.Item;
public final class MicrocontrollerCaseItem extends Item implements li.cil.oc.api.internal.Tiered {
    public final int tier;
    public MicrocontrollerCaseItem(Properties properties,int tier){super(properties.stacksTo(1));this.tier=tier;}
    @Override public int tier(){return tier;}
}
