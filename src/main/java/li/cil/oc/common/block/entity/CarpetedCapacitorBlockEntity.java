package li.cil.oc.common.block.entity;

import li.cil.oc.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.List;

/** The original sheep/ocelot group generator, including its shared one-minute shock cooldown. */
public final class CarpetedCapacitorBlockEntity extends CapacitorBlockEntity {
    private long nextChanceTime;
    public CarpetedCapacitorBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) { super(type, position, state); }
    @Override protected String product() { return "CarpetedCapBank3x"; }
    @Override public void serverTick() {
        super.serverTick();
        if (!server() || !isPowerAvailable() || Math.floorMod(level.getGameTime() + hashCode(), 20) != 0) return;
        var entities = level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition.above()), LivingEntity::isAlive);
        double power = energyFromGroup(entities.stream().filter(Sheep.class::isInstance).toList(), Settings.get().sheepPower)
                // Minecraft 1.12's EntityOcelot represented both wild ocelots and domestic cats.
                + energyFromGroup(entities.stream().filter(entity -> entity instanceof Ocelot || entity instanceof Cat).toList(), Settings.get().ocelotPower);
        if (power > 0 && node() != null) { node().changeBuffer(power); setChanged(); }
    }
    private double energyFromGroup(List<LivingEntity> entities, double power) {
        if (entities.size() < 2) return 0;
        if (Settings.get().carpetDamageChance > 0 && nextChanceTime < level.getGameTime()) {
            for (LivingEntity entity : entities) if (level.random.nextDouble() < Settings.get().carpetDamageChance) {
                entity.hurt(level.damageSources().generic(), 1);
                entity.setLastHurtByMob(entity);
                entity.knockback(0, 0.25, 0);
                nextChanceTime = level.getGameTime() + 20 * 60;
                break;
            }
        }
        return power;
    }
}
