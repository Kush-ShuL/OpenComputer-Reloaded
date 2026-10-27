package li.cil.oc.common.item;

import java.util.List;
import java.util.TreeMap;
import li.cil.oc.api.internal.Tiered;
import li.cil.oc.common.inventory.ServerInventory;
import li.cil.oc.common.menu.ServerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class ServerItem extends Item implements Tiered {
    private final int tier;
    public ServerItem(Properties properties,int tier){super(properties.stacksTo(1).rarity(tier==0?Rarity.COMMON:tier==1?Rarity.UNCOMMON:tier==2?Rarity.RARE:Rarity.EPIC));this.tier=tier;}
    @Override public int tier(){return tier;}
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book){return false;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(!player.isShiftKeyDown()&&player instanceof ServerPlayer server)ServerMenu.openHeld(server,hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);for(String line:Component.translatable("oc:tooltip.server").getString().split("\\[nl\\]"))tooltip.add(Component.literal(line));
        if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT&&ClientInput.extended()&&context.registries()!=null){TreeMap<String,Integer> counts=new TreeMap<>();for(ItemStack component:ServerInventory.loadItems(stack,context.registries()))if(!component.isEmpty())counts.merge(component.getHoverName().getString(),1,Integer::sum);if(!counts.isEmpty()){tooltip.add(Component.translatable("oc:tooltip.server.Components"));counts.forEach((name,count)->tooltip.add(Component.literal("- "+count+"x "+name)));}}
        var node=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("oc:data").getCompound("node");if(node.contains("address")){String address=node.getString("address");tooltip.add(Component.literal("§8"+address.substring(0,Math.min(13,address.length()))+"...§7"));}
    }
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static final class ClientInput {
        static boolean extended(){
            var minecraft=net.minecraft.client.Minecraft.getInstance();if(minecraft==null||minecraft.getWindow()==null)return false;
            var key=minecraft.options.keyShift.getKey();long window=minecraft.getWindow().getWindow();
            return switch(key.getType()){
                case MOUSE->org.lwjgl.glfw.GLFW.glfwGetMouseButton(window,key.getValue())==org.lwjgl.glfw.GLFW.GLFW_PRESS;
                case KEYSYM->key.getValue()>=0&&com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,key.getValue());
                default->false;
            };
        }
    }
}
