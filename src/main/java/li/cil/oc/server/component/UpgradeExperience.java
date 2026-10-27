package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.internal.*;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.HashMap;
import java.util.Map;

public final class UpgradeExperience extends UpgradeBase {
    public static final int MAX_LEVEL = 30;
    private final Agent agent;
    private double experience;
    private int level;

    public UpgradeExperience(EnvironmentHost host) {
        super(host, "experience", "Knowledge database", "ERSO (Event Recorder and Self-Optimizer)", true);
        agent = (Agent) host;
        ((li.cil.oc.api.network.Component) node()).setVisibility(Visibility.Network);
        // Node data loads before XP, so initially allow the original maximum capacity.
        // Starting at zero would discard saved connector energy before restoring the level.
        connector().setLocalBufferSize(MAX_LEVEL * Settings.get().bufferPerLevel);
    }

    @Override public Map<String, String> getDeviceInfo() {
        var info = new HashMap<>(super.getDeviceInfo());
        info.put("capacity", Integer.toString(MAX_LEVEL));
        return Map.copyOf(info);
    }

    public int level() { return level; }
    public double experience() { return experience; }
    public static double xpForLevel(int level) {
        return level == 0 ? 0 : Settings.get().baseXpToLevel
                + Math.pow(level * Settings.get().constantXpGrowth, Settings.get().exponentialXpGrowth);
    }

    private void updateLevel() {
        int previous = level;
        // Preserve the pinned source's inverse formula, including Java/Scala's NaN-to-int conversion.
        level = Math.min(MAX_LEVEL, (int) (Math.pow(experience - Settings.get().baseXpToLevel,
                1 / Settings.get().exponentialXpGrowth) / Settings.get().constantXpGrowth));
        if (level != previous && agent instanceof li.cil.oc.api.internal.Robot robot) robot.synchronizeSlot(agent.componentSlot(node().address()));
        connector().setLocalBufferSize(level * Settings.get().bufferPerLevel);
    }

    public void addExperience(double amount) {
        if (level >= MAX_LEVEL) return;
        experience += amount;
        if (experience >= xpForLevel(level + 1)) updateLevel();
        var player = agent.player();
        var position = player.blockPosition();
        var orb = new ExperienceOrb(host.world(), position.getX() + .5, position.getY() + .5, position.getZ() + .5, (int) amount);
        player.takeXpDelay = 0;
        // The original awards the same integer XP through normal orb pickup, including mending.
        // Do not also call giveExperiencePoints: that would award the player's share twice.
        orb.playerTouch(player);
        changed();
    }

    @Callback(direct = true, doc = "function():number -- The current level of experience stored in this experience upgrade.")
    public Object[] level(Context context, Arguments args) {
        double needed = xpForLevel(level + 1) - xpForLevel(level);
        double progress = Math.max(0, experience - xpForLevel(level));
        return new Object[]{level + progress / needed};
    }

    @Callback(doc = "function():boolean -- Tries to consume an enchanted item to add experience to the upgrade.")
    public Object[] consume(Context context, Arguments args) {
        if (level >= MAX_LEVEL) return new Object[]{null, "max level"};
        var inventory = agent.mainInventory();
        if (inventory.getContainerSize() == 0) return new Object[]{null, "no item"};
        ItemStack stack = inventory.getItem(agent.selectedSlot());
        if (stack.isEmpty()) return new Object[]{null, "no item"};
        int xp = 0;
        if (stack.is(Items.EXPERIENCE_BOTTLE)) {
            xp = 3 + host.world().random.nextInt(5) + host.world().random.nextInt(5);
        } else {
            for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
                xp += entry.getKey().value().getMinCost(entry.getIntValue());
            }
            if (xp <= 0) return new Object[]{null, "could not extract experience from item"};
        }
        // Original consume destroys one entire item, including enchanted tools/books.
        // It neither strips enchantments in place nor returns a crafting remainder.
        if (inventory.removeItem(agent.selectedSlot(), 1).isEmpty()) return new Object[]{null, "could not consume item"};
        addExperience(xp * Settings.get().constantXpGrowth);
        return new Object[]{true};
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        experience = Math.max(0, tag.getDouble(Settings.namespace + "xp"));
        updateLevel();
    }

    @Override public void save(CompoundTag tag) {
        super.save(tag);
        tag.putDouble(Settings.namespace + "xp", experience);
    }
}
