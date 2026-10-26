package li.cil.oc.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import li.cil.oc.common.block.entity.PrinterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;

/** Original rotating preview renders the configured Print item, including all actual shapes. */
public final class PrinterBlockEntityRenderer implements BlockEntityRenderer<PrinterBlockEntity> {
    public PrinterBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(PrinterBlockEntity printer, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (printer.getLevel() == null || printer.preview().stateOff.isEmpty()) return;
        var stack = printer.preview().createItemStack(printer.getLevel().registryAccess());
        pose.pushPose(); pose.translate(.5, .8, .5); pose.mulPose(Axis.YP.rotationDegrees((System.currentTimeMillis() % 20000) / 20000f * 360)); pose.scale(.75f, .75f, .75f);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, printer.getLevel(), 0);
        pose.popPose();
    }
}
