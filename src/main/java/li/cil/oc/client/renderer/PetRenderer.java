package li.cil.oc.client.renderer;

import com.mojang.math.Axis;
import li.cil.oc.Settings;
import li.cil.oc.common.network.PetNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Original supporter companions: the original UUIDs, colors, scale and following animation. */
public final class PetRenderer {
    private static final Map<UUID,Integer> ENTITLED=Map.ofEntries(
        profile("9f1f262f-0d68-4e13-9161-9eeaf4a0a1a8",.3,.9,.6),
        profile("18f8bed4-f027-44af-8947-6a3a2317645a",1,0,0),
        profile("36123742-2cf6-4cfc-8b65-278581b3caeb",.5,.7,1),
        profile("2c0c214b-96f4-4565-b513-de90d5fbc977",1,0,0),
        profile("f3ba6ec8-c280-4950-bb08-1fcb2eab3a9c",.18,.95,.922),
        profile("9d636bdd-b9f4-4b80-b9ce-586ca04bd4f3",.8,.77,.75),
        profile("23c7ed71-fb13-4abe-abe7-f355e1de6e62",.3,.3,1),
        profile("076541f1-f10a-46de-a127-dfab8adfbb75",.2,1,.1),
        profile("e7e90198-0ccf-4662-a827-192ec8f4419d",0,.2,.6),
        profile("f514ee69-7bbb-4e46-9e94-d8176324cec2",.098,.471,.784),
        profile("f812c043-78ba-4324-82ae-e8f05c52ae6e",.1,.8,.5));
    private static final Set<UUID> HIDDEN=new HashSet<>();
    private static final Map<Player,Location> LOCATIONS=new WeakHashMap<>();
    private static final ThreadLocal<Integer> TINT=new ThreadLocal<>();
    private static Boolean sentVisible;
    private PetRenderer(){}
    private static Map.Entry<UUID,Integer> profile(String uuid,double r,double g,double b){return Map.entry(UUID.fromString(uuid),0xff000000|(int)Math.round(r*255)<<16|(int)Math.round(g*255)<<8|(int)Math.round(b*255));}
    public static Integer chassisTint(){return TINT.get();}
    public static void login(ClientPlayerNetworkEvent.LoggingIn event){clear();synchronize();}
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    private static void clear(){HIDDEN.clear();LOCATIONS.clear();sentVisible=null;TINT.remove();}
    public static void receive(PetNetworking.Visibility packet){if(Minecraft.getInstance().player==null)return;if(packet.reset())HIDDEN.clear();packet.players().forEach((id,visible)->{if(visible)HIDDEN.remove(id);else HIDDEN.add(id);});if(Settings.get().hideOwnPet)HIDDEN.add(Minecraft.getInstance().player.getUUID());}
    private static void synchronize(){var client=Minecraft.getInstance();if(client.player==null||client.getConnection()==null)return;boolean visible=!Settings.get().hideOwnPet;if(sentVisible!=null&&sentVisible==visible)return;sentVisible=visible;if(visible)HIDDEN.remove(client.player.getUUID());else HIDDEN.add(client.player.getUUID());PacketDistributor.sendToServer(new PetNetworking.SetVisibility(visible));}
    public static void tick(ClientTickEvent.Pre event){synchronize();long now=System.nanoTime();LOCATIONS.entrySet().removeIf(entry->entry.getKey().isRemoved()||now-entry.getValue().lastSeen>5_000_000_000L);for(var entry:LOCATIONS.entrySet())entry.getValue().update(entry.getKey());}
    public static void render(RenderPlayerEvent.Pre event){Player owner=event.getEntity();Integer tint=ENTITLED.get(owner.getUUID());if(tint==null||HIDDEN.contains(owner.getUUID()))return;var client=Minecraft.getInstance();if(client.player==null||owner==client.player&&Settings.get().hideOwnPet)return;Location location=LOCATIONS.computeIfAbsent(owner,Location::new);location.lastSeen=System.nanoTime();float partial=event.getPartialTick();double worldTime=owner.level().getGameTime();double hover=Math.sin((owner.hashCode()^0xff)+(worldTime+partial)/20)*.03;var pose=event.getPoseStack();pose.pushPose();Integer previous=TINT.get();TINT.set(tint);try{double correction=1.62-(owner.isShiftKeyDown()?.125:0);pose.translate(0,owner.getEyeHeight()-client.player.getEyeHeight()+correction,0);location.apply(owner,pose,partial);pose.scale(.3f,.3f,.3f);pose.translate(0,hover,0);RobotBlockEntityRenderer.drawChassis(pose,event.getMultiBufferSource(),event.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,true,0xf23030,worldTime+partial,null);}finally{if(previous==null)TINT.remove();else TINT.set(previous);pose.popPose();}li.cil.oc.client.RenderDiagnostics.checkError("PetRenderer.render");}
    private static final class Location {
        double x,y,z,lastX,lastY,lastZ;float yaw,lastYaw;long lastSeen=System.nanoTime();
        Location(Player owner){yaw=lastYaw=owner.getYRot();}
        void update(Player owner){lastX=x;lastY=y;lastZ=z;lastYaw=yaw;x=(x+owner.xo-owner.getX())*.05;y=(y+owner.yo-owner.getY())*.05;z=(z+owner.zo-owner.getZ())*.05;yaw+=(owner.getYRot()-yaw)*.2f;}
        void apply(Player owner,com.mojang.blaze3d.vertex.PoseStack pose,float partial){pose.translate(lastX+(x-lastX)*partial,lastY+(y-lastY)*partial,lastZ+(z-lastZ)*partial);float rotation=Minecraft.getInstance().screen!=null&&owner==Minecraft.getInstance().player?owner.getYRot():lastYaw+(yaw-lastYaw)*partial;pose.mulPose(Axis.YP.rotationDegrees(-rotation));pose.translate(.3,-.1,-.2);}
    }
}
