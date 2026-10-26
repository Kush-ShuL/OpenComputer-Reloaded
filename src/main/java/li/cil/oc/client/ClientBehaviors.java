package li.cil.oc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.Settings;
import li.cil.oc.client.renderer.PetRenderer;
import li.cil.oc.common.block.entity.RobotBlockEntity;
import li.cil.oc.common.network.PetNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import java.time.LocalDate;

/** Physical-client hooks for the original optional presentation and diagnostic settings. */
public final class ClientBehaviors {
    private static boolean aprilFools=isAprilFools();private static int ticks;
    private ClientBehaviors(){}
    private static boolean isAprilFools(){LocalDate date=LocalDate.now();return date.getMonthValue()==4&&date.getDayOfMonth()==1;}
    public static void register(IEventBus bus){PetNetworking.clientReceiver(PetRenderer::receive);NeoForge.EVENT_BUS.addListener(PetRenderer::login);NeoForge.EVENT_BUS.addListener(PetRenderer::logout);NeoForge.EVENT_BUS.addListener(PetRenderer::render);NeoForge.EVENT_BUS.addListener(PetRenderer::tick);NeoForge.EVENT_BUS.addListener(ClientBehaviors::tick);NeoForge.EVENT_BUS.addListener(ClientBehaviors::logout);bus.addListener(ClientBehaviors::reload);}
    private static void tick(ClientTickEvent.Pre event){if(++ticks%1200==0)aprilFools=isAprilFools();}
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event){RenderDiagnostics.clear();ticks=0;aprilFools=isAprilFools();}
    private static void reload(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)resources->RenderDiagnostics.clear());}
    public static void robotLabel(RobotBlockEntity robot,PoseStack pose,MultiBufferSource buffers,int light){if(!Settings.get().robotLabels)return;String name=robot.controller().name();if(name==null||name.isEmpty())return;var client=Minecraft.getInstance();if(client.options.hideGui||client.getEntityRenderDispatcher().camera==null)return;var camera=client.getEntityRenderDispatcher().camera;if(camera.getPosition().distanceToSqr(Vec3.atLowerCornerOf(robot.getBlockPos()))>=64)return;Component text=Component.literal(name);if(aprilFools)text=text.copy().withStyle(ChatFormatting.OBFUSCATED);pose.pushPose();pose.translate(.5,1.3,.5);pose.mulPose(client.getEntityRenderDispatcher().cameraOrientation());pose.scale(-1.6f/60,-1.6f/60,1.6f/60);client.font.drawInBatch(text,-client.font.width(text)/2f,0,0xffffffff,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0x80000000,light);pose.popPose();}
}
