package li.cil.oc.server.agent;

import com.mojang.authlib.GameProfile;
import li.cil.oc.Settings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.core.Direction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** One owner-attributed fake player per agent, never a global shared tool inventory. */
public final class AgentPlayer extends FakePlayer {
    private final AgentHost host;
    private AgentInventory agentInventory;
    public boolean usingSelectedMaterial;
    private ItemStack previousTool=ItemStack.EMPTY;
    public AgentPlayer(ServerLevel level, AgentHost host) {
        super(level, new GameProfile(host.ownerUUID(),Settings.get().nameFormat.replace("$player$",host.ownerName()).replace("$random$",Integer.toString(level.random.nextInt(0xFFFFFF)+1))));
        this.host = host; agentInventory = new AgentInventory(this, host);
        gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        getAbilities().mayfly=true;getAbilities().flying=true;getAbilities().invulnerable=true;setOnGround(true);
    }
    @Override public Inventory getInventory() { return agentInventory == null ? super.getInventory() : agentInventory; }
    @Override public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (host == null) return super.getItemBySlot(slot);
        if (slot != EquipmentSlot.MAINHAND) return ItemStack.EMPTY;
        return usingSelectedMaterial ? selected() : host.equipmentInventory().getContainerSize() > 0 ? host.equipmentInventory().getItem(0) : ItemStack.EMPTY;
    }
    @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (host == null) { super.setItemSlot(slot, stack); return; }
        if (slot != EquipmentSlot.MAINHAND) return;
        if (usingSelectedMaterial) host.mainInventory().setItem(host.selectedSlot(), stack);
        else if (host.equipmentInventory().getContainerSize() > 0) host.equipmentInventory().setItem(0, stack);
        host.inventoryChanged();
    }
    private ItemStack selected() { return host.selectedSlot() >= 0 && host.selectedSlot() < host.mainInventory().getContainerSize() ? host.mainInventory().getItem(host.selectedSlot()) : ItemStack.EMPTY; }
    public AgentPlayer orient(Direction facing, boolean sneak) {
        ItemStack tool=getMainHandItem();
        if(!ItemStack.isSameItemSameComponents(previousTool,tool)){
            previousTool.forEachModifier(EquipmentSlot.MAINHAND,(attribute,modifier)->{var instance=getAttribute(attribute);if(instance!=null)instance.removeModifier(modifier.id());});
            tool.forEachModifier(EquipmentSlot.MAINHAND,(attribute,modifier)->{var instance=getAttribute(attribute);if(instance!=null){instance.removeModifier(modifier.id());instance.addTransientModifier(modifier);}});previousTool=tool.copy();
        }
        setPos(host.xPosition(), host.yPosition() - getEyeHeight(), host.zPosition());
        setYRot(facing.getAxis().isVertical() ? host.facing().toYRot() : facing.toYRot());
        setXRot(facing == Direction.DOWN ? 90 : facing == Direction.UP ? -90 : 0);
        setShiftKeyDown(sneak); return this;
    }
    public AgentHost agent(){return host;}
    @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player player){return Settings.get().canAttackPlayers&&super.canHarmPlayer(player);}
    @Override public float getAttackStrengthScale(float partial){return 1;}
    @Override public void causeFoodExhaustion(float amount){
        if(host==null)return;
        if(Settings.get().robotExhaustionCost>0&&host.machine()!=null&&host.machine().node() instanceof li.cil.oc.api.network.Connector connector)
            connector.changeBuffer(-Settings.get().robotExhaustionCost*amount);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new li.cil.oc.api.event.RobotExhaustionEvent(host,amount));
    }
    public static UUID determineUUID(UUID owner){UUID random=UUID.randomUUID();try{return UUID.fromString(Settings.get().uuidFormat.replace("$random$",random.toString()).replace("$player$",owner.toString()));}catch(IllegalArgumentException ignored){return random;}}
}
