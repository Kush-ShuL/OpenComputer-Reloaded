package li.cil.oc.common.item;

import li.cil.oc.api.internal.Tiered;
import li.cil.oc.server.component.UpgradeExperience;
import net.minecraft.world.item.Item;

/** Original upgrade item identity; behavior lives in its managed environment. */
public final class FunctionalUpgradeItem extends Item implements Tiered,li.cil.oc.api.driver.item.Chargeable {
    public enum Kind { ANGEL,BATTERY,DATABASE,EXPERIENCE,GENERATOR,HOVER,SOLAR,TANK,CRAFTING,CHUNKLOADER,INVENTORY_CONTROLLER,TANK_CONTROLLER,SIGN,LEASH,PISTON,STICKY_PISTON,TRACTOR,NAVIGATION,TRADING }
    public final Kind kind;private final int tier;
    public FunctionalUpgradeItem(Properties properties,Kind kind,int tier){super(properties.rarity(switch(tier){case 0->net.minecraft.world.item.Rarity.COMMON;case 1->net.minecraft.world.item.Rarity.UNCOMMON;default->net.minecraft.world.item.Rarity.RARE;}));this.kind=kind;this.tier=tier;}
    @Override public int tier(){return tier;}
    @Override public int getMaxStackSize(net.minecraft.world.item.ItemStack stack){var data=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data");return data.getCompound("node").contains("address")?1:super.getMaxStackSize(stack);}
    @Override public boolean canCharge(net.minecraft.world.item.ItemStack stack){return kind==Kind.BATTERY;}
    @Override public double charge(net.minecraft.world.item.ItemStack stack,double amount,boolean simulate){if(!canCharge(stack)||Double.isNaN(amount))return amount;var data=new li.cil.oc.common.item.data.NodeData(stack);double stored=data.buffer.orElse(0d),maximum=li.cil.oc.Settings.get().bufferCapacitorUpgrades[tier],accepted=Math.clamp(stored+amount,0,maximum)-stored;if(!simulate&&(accepted>Double.MIN_VALUE||accepted< -Double.MIN_VALUE)){data.buffer=java.util.Optional.of(stored+accepted);var root=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();data.save(root);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(root));}return amount-accepted;}
    @Override public boolean isBarVisible(net.minecraft.world.item.ItemStack stack){return kind==Kind.BATTERY;}
    @Override public int getBarWidth(net.minecraft.world.item.ItemStack stack){return kind==Kind.BATTERY?(int)Math.clamp(Math.round(13*new li.cil.oc.common.item.data.NodeData(stack).buffer.orElse(0d)/li.cil.oc.Settings.get().bufferCapacitorUpgrades[tier]),0,13):0;}
    @Override public int getBarColor(net.minecraft.world.item.ItemStack stack){return kind==Kind.BATTERY?net.minecraft.util.Mth.hsvToRgb(Math.max(0,(float)(new li.cil.oc.common.item.data.NodeData(stack).buffer.orElse(0d)/li.cil.oc.Settings.get().bufferCapacitorUpgrades[tier]))/3,1,1):0x55cc88;}
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,TooltipContext context,java.util.List<net.minecraft.network.chat.Component> tooltip,net.minecraft.world.item.TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);String name=switch(kind){case SOLAR->"solargenerator";case INVENTORY_CONTROLLER->"inventorycontroller";case TANK_CONTROLLER->"tankcontroller";case STICKY_PISTON->"stickypiston";case TRACTOR->"tractorbeam";default->kind.name().toLowerCase(java.util.Locale.ROOT);};Object[] values=switch(kind){case BATTERY->new Object[]{(int)li.cil.oc.Settings.get().bufferCapacitorUpgrades[tier]};case DATABASE->new Object[]{new int[]{9,25,81}[tier]};case HOVER->new Object[]{li.cil.oc.Settings.get().upgradeFlightHeight[tier]};case GENERATOR->new Object[]{(int)(li.cil.oc.Settings.get().generatorEfficiency*100)};case SOLAR->new Object[]{(int)(li.cil.oc.Settings.get().solarGeneratorEfficiency*100)};default->new Object[0];};for(String line:net.minecraft.network.chat.Component.translatable("oc:tooltip.upgrade"+name,values).getString().split("\\[nl\\]"))tooltip.add(net.minecraft.network.chat.Component.literal(line));if(kind==Kind.EXPERIENCE){
        var data=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data");
        double xp=UpgradeExperience.getExperience(data);
        int level=UpgradeExperience.calculateLevelFromExperience(xp);
        double reported=UpgradeExperience.calculateExperienceLevel(level,xp);
        tooltip.add(net.minecraft.network.chat.Component.translatable("oc:tooltip.robot_level",Double.toString(reported)));
    }
        var node=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data").getCompound("node");
        if(node.contains("address")){
            String address=node.getString("address");
            tooltip.add(net.minecraft.network.chat.Component.literal("§8"+address.substring(0,Math.min(13,address.length()))+"...§7"));
        }
        if(flag.isAdvanced())tooltip.add(net.minecraft.network.chat.Component.translatable("oc:tooltip.tier",tier+1));
    }
    @Override public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(kind!=Kind.DATABASE)return net.minecraft.world.InteractionResultHolder.pass(stack);
        if(player.isShiftKeyDown()){
            var data=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data");
            if(data.contains("oc:items")){
                stack=new net.minecraft.world.item.ItemStack(this,stack.getCount());
                player.setItemInHand(hand,stack);
                player.getInventory().setChanged();
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }else{
            if(player instanceof net.minecraft.server.level.ServerPlayer server){
                int index=hand==net.minecraft.world.InteractionHand.MAIN_HAND?player.getInventory().selected:40;
                server.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,owner)->new li.cil.oc.common.menu.DatabaseMenu(id,inventory,index),stack.getHoverName()),buffer->buffer.writeVarInt(index));
            }
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        return net.minecraft.world.InteractionResultHolder.success(stack);
    }
}
