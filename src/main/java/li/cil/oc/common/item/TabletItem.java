package li.cil.oc.common.item;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.item.Chargeable;
import li.cil.oc.common.item.data.TabletData;
import li.cil.oc.common.menu.TabletMenu;
import li.cil.oc.server.portable.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Short use opens the terminal, long use scans, sneak-use exposes the dynamic bay. */
public final class TabletItem extends Item implements Chargeable {
    private record Scan(Level level,BlockPos pos,Direction side,Vec3 hit){}
    private static final Map<UUID,Scan> SCANS=new ConcurrentHashMap<>();
    public TabletItem(Properties properties){super(properties.stacksTo(1));}
    @Override public boolean isBookEnchantable(ItemStack stack,ItemStack book){return false;}
    @Override public void inventoryTick(ItemStack stack,Level world,Entity entity,int slot,boolean selected){if(entity instanceof Player player){if(!world.isClientSide)TabletManager.get(stack,player).tick();else if(player.getUseItem()==stack&&player.getTicksUsingItem()==10)li.cil.oc.server.PacketSender.playLocalSound(world,player.getX(),player.getY()+2,player.getZ(),".");}}
    @Override public int getUseDuration(ItemStack stack,LivingEntity user){return 72000;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
    @Override public InteractionResultHolder<ItemStack> use(Level world,Player player,InteractionHand hand){if(!world.isClientSide)SCANS.remove(player.getUUID());player.startUsingItem(hand);return InteractionResultHolder.consume(player.getItemInHand(hand));}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){Player player=context.getPlayer();if(player==null)return InteractionResult.PASS;if(!context.getLevel().isClientSide)SCANS.put(player.getUUID(),new Scan(context.getLevel(),context.getClickedPos().immutable(),context.getClickedFace(),context.getClickLocation().subtract(Vec3.atLowerCornerOf(context.getClickedPos()))));player.startUsingItem(context.getHand());return InteractionResult.sidedSuccess(context.getLevel().isClientSide);}
    @Override public void releaseUsing(ItemStack stack,Level world,LivingEntity user,int remaining){if(world.isClientSide||!(user instanceof ServerPlayer player))return;TabletHost host=TabletManager.get(stack,player);if(!host.stillValid(player))return;Scan target=SCANS.remove(player.getUUID());
        if(getUseDuration(stack,user)-remaining>=10){if(target!=null&&target.level==world&&player.distanceToSqr(Vec3.atCenterOf(target.pos))<=64)host.analyze(target.pos,target.side,target.hit.x,target.hit.y,target.hit.z);return;}
        if(player.isShiftKeyDown()){host.machine().stop();host.displayChanged();if(host.data.tier>0)player.openMenu(new SimpleMenuProvider((id,inventory,viewer)->new TabletMenu(id,inventory,host),Component.translatable("item.opencomputers.tablet")),buffer->{buffer.writeUtf(host.id);ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer,host.data.container);});}
        else{if(!host.machine().isRunning()&&!host.machine().start()){String error=host.machine().lastError();if(error!=null)player.displayClientMessage(Component.literal(error),true);}li.cil.oc.common.network.PortableNetworking.open(player,host);}host.save(false);
    }
    @Override public boolean isBarVisible(ItemStack stack){return true;}
    @Override public int getBarWidth(ItemStack stack){return (int)Math.clamp(Math.round(13*energy(stack)/Math.max(1,capacity(stack))),0,13);}
    @Override public int getBarColor(ItemStack stack){return net.minecraft.util.Mth.hsvToRgb((float)Math.clamp(energy(stack)/Math.max(1,capacity(stack)),0,1)/3,1,1);}
    private static double energy(ItemStack stack){TabletHost host=TabletManager.peek(stack);return host==null?stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getDouble("oc:energy"):host.connector().globalBuffer();}
    private static double capacity(ItemStack stack){TabletHost host=TabletManager.peek(stack);return host==null?stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getDouble("oc:maxEnergy"):host.connector().globalBufferSize();}
    @Override public boolean canCharge(ItemStack stack){return stack.getItem()==this;}
    @Override public double charge(ItemStack stack,double amount,boolean simulate){if(amount<=0||Double.isNaN(amount))return amount;TabletHost host=TabletManager.peek(stack);if(host!=null)return host.charge(amount,simulate);double accepted=Math.min(amount,Math.max(0,capacity(stack)-energy(stack)));if(!simulate&&accepted>0){var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putDouble("oc:energy",energy(stack)+accepted);tag.putDouble("oc:maxEnergy",capacity(stack));stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}return amount-accepted;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){super.appendHoverText(stack,context,tooltip,flag);tooltip.add(Component.literal(String.format("%.0f / %.0f OC",energy(stack),capacity(stack))));for(String line:Component.translatable("oc:tooltip.tablet").getString().split("\\[nl\\]"))tooltip.add(Component.literal(line));if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT&&ClientInput.extended()&&context.registries()!=null){tooltip.add(Component.translatable("oc:tooltip.server.Components"));ItemStack[] components=new TabletData(stack,context.registries()).items;for(int slot=1;slot<components.length;slot++)if(!components[slot].isEmpty())tooltip.add(Component.literal("- ").append(components[slot].getHoverName()));}}
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static final class ClientInput{
        static boolean extended(){var client=net.minecraft.client.Minecraft.getInstance();var key=client.options.keyShift.getKey();long window=client.getWindow().getWindow();return switch(key.getType()){case MOUSE->org.lwjgl.glfw.GLFW.glfwGetMouseButton(window,key.getValue())==org.lwjgl.glfw.GLFW.GLFW_PRESS;case KEYSYM->key.getValue()>=0&&com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,key.getValue());default->false;};}
    }
}
