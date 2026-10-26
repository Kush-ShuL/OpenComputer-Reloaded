package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.internal.*;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.EnvironmentHost;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class UpgradeExperience extends UpgradeBase {
    private final Agent agent;private double experience;private int level;
    public UpgradeExperience(EnvironmentHost host){super(host,"experience","Knowledge database","ERSO (Event Recorder and Self-Optimizer)",true);agent=(Agent)host;connector().setLocalBufferSize(0);}
    public int level(){return level;}
    public double experience(){return experience;}
    public static double xpForLevel(int level){return level==0?0:Settings.get().baseXpToLevel+Math.pow(level*Settings.get().constantXpGrowth,Settings.get().exponentialXpGrowth);}
    private void updateLevel(){int previous=level;level=Math.max(0,Math.min(30,(int)(Math.pow(Math.max(0,experience-Settings.get().baseXpToLevel),1/Settings.get().exponentialXpGrowth)/Settings.get().constantXpGrowth)));connector().setLocalBufferSize(level*Settings.get().bufferPerLevel);if(level!=previous&&agent instanceof li.cil.oc.api.internal.Robot robot)robot.synchronizeSlot(agent.componentSlot(node().address()));changed();}
    public void addExperience(double amount){if(level<30&&amount>0){experience+=amount;updateLevel();var player=agent.player();player.takeXpDelay=0;new net.minecraft.world.entity.ExperienceOrb(host.world(),player.getX(),player.getY(),player.getZ(),(int)amount).playerTouch(player);}}
    @Callback(direct=true) public Object[] level(Context context,Arguments args){return new Object[]{level>=30?30d:level+Math.max(0,experience-xpForLevel(level))/(xpForLevel(level+1)-xpForLevel(level))};}
    @Callback public Object[] consume(Context context,Arguments args){if(level>=30)return new Object[]{null,"max level"};if(agent.mainInventory().getContainerSize()==0)return new Object[]{null,"no item"};ItemStack stack=agent.mainInventory().getItem(agent.selectedSlot());if(stack.isEmpty())return new Object[]{null,"no item"};int xp=0;if(stack.is(Items.EXPERIENCE_BOTTLE))xp=3+host.world().random.nextInt(5)+host.world().random.nextInt(5);else for(var entry:EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet())xp+=entry.getKey().value().getMinCost(entry.getIntValue());if(xp<=0)return new Object[]{null,"could not extract experience from item"};if(agent.mainInventory().removeItem(agent.selectedSlot(),1).isEmpty())return new Object[]{null,"could not consume item"};addExperience(xp*Settings.get().constantXpGrowth);changed();return new Object[]{true};}
    @Override public void load(CompoundTag tag){super.load(tag);experience=Math.max(0,tag.getDouble("oc:xp"));updateLevel();}
    @Override public void save(CompoundTag tag){super.save(tag);tag.putDouble("oc:xp",experience);}
}
