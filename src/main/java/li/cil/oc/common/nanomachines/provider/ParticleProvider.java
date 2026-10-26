package li.cil.oc.common.nanomachines.provider;
import li.cil.oc.Settings;
import li.cil.oc.api.nanomachines.*;
import li.cil.oc.api.prefab.*;
import li.cil.oc.common.nanomachines.Nanomachines;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import java.util.*;
public final class ParticleProvider extends AbstractProvider {
    public static final ParticleProvider INSTANCE=new ParticleProvider();
    private static final int[] LEGACY={3,22,11,17,23,25,26,27,5,30,33,34,21};
    private static final String[] NAMES={"fireworksSpark","townaura","smoke","witchMagic","note","enchantmenttable","flame","lava","splash","reddust","slime","heart","happyVillager"};
    private ParticleProvider(){super("b48c4bbd-51bb-4915-9367-16cff3220e4b");}
    public Iterable<Behavior> createBehaviors(Player player){List<Behavior> result=new ArrayList<>();for(int index=0;index<LEGACY.length;index++)result.add(new ParticleBehavior(index,player));return result;}
    protected void writeBehaviorToNBT(Behavior behavior,CompoundTag nbt){nbt.putInt("effectName",LEGACY[((ParticleBehavior)behavior).index]);}
    protected Behavior readBehaviorFromNBT(Player player,CompoundTag nbt){int id=nbt.getInt("effectName");for(int index=0;index<LEGACY.length;index++)if(LEGACY[index]==id)return new ParticleBehavior(index,player);return null;}
    private static ParticleOptions particle(int index){return switch(index){case 0->ParticleTypes.FIREWORK;case 1->ParticleTypes.MYCELIUM;case 2->ParticleTypes.SMOKE;case 3->ParticleTypes.WITCH;case 4->ParticleTypes.NOTE;case 5->ParticleTypes.ENCHANT;case 6->ParticleTypes.FLAME;case 7->ParticleTypes.LAVA;case 8->ParticleTypes.SPLASH;case 9->DustParticleOptions.REDSTONE;case 10->new ItemParticleOption(ParticleTypes.ITEM,new ItemStack(Items.SLIME_BALL));case 11->ParticleTypes.HEART;default->ParticleTypes.HAPPY_VILLAGER;};}
    public static final class ParticleBehavior extends AbstractBehavior {final int index;ParticleBehavior(int index,Player player){super(player);this.index=index;}public String getNameHint(){return"particles."+NAMES[index];}public void update(){if(player.level().isClientSide&&Settings.get().enableNanomachinePfx){Controller controller=li.cil.oc.api.Nanomachines.getController(player);if(controller!=null)Nanomachines.spawnParticle(player,particle(index),controller.getInputCount(this)*.25);}}}
}
