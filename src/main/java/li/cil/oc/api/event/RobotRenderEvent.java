package li.cil.oc.api.event;

import li.cil.oc.api.driver.item.UpgradeRenderer;
import li.cil.oc.api.internal.Agent;
import li.cil.oc.api.internal.Robot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Set;

/**
 * Fired directly before the robot's chassis is rendered.
 * <br>
 * If this event is canceled, the chassis will <em>not</em> be rendered.
 * Component items' item renderers will still be invoked, at the possibly
 * modified mount points.
 * <br>
 * <em>Important</em>: the robot instance may be null in this event, in
 * case the render pass is for rendering the robot in an inventory.
 */
public class RobotRenderEvent extends RobotEvent implements ICancellableEvent {
    public final com.mojang.blaze3d.vertex.PoseStack poseStack;
    public final net.minecraft.client.renderer.MultiBufferSource buffers;
    public final int packedLight;
    public final int packedOverlay;
    /**
     * Points on the robot at which component models may be rendered.
     * <br>
     * By convention, components should be rendered in order of their slots,
     * meaning that some components may not be rendered at all, if there are
     * not enough mount points.
     * <br>
     * The equipped tool is rendered at a fixed position, this list does not
     * contain a mount point for it.
     */
    public final MountPoint[] mountPoints;

    public RobotRenderEvent(Agent agent, MountPoint[] mountPoints,
                            com.mojang.blaze3d.vertex.PoseStack poseStack,
                            net.minecraft.client.renderer.MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super(agent);
        this.mountPoints = mountPoints;
        this.poseStack = poseStack;
        this.buffers = buffers;
        this.packedLight = packedLight;
        this.packedOverlay = packedOverlay;
    }

    /**
     * Describes points on the robot model at which components are "mounted",
     * i.e. where component models may be rendered.
     */
    public static class MountPoint {
        /**
         * The position of the mount point, relative to the robot's center.
         * For the purposes of this offset, the robot is always facing south,
         * i.e. the positive Z axis is 'forward'.
         * <br>
         * Note that the rotation is applied <em>before</em> the translation.
         */
        public final Vector3f offset = new Vector3f(0, 0, 0);

        /**
         * The orientation of the mount point specified by the angle and the
         * vector to rotate around. The rotation is applied in one
         * GL11.glRotate() call. Note that the <tt>W</tt> component of the
         * vector is the rotation.
         * <br>
         * Note that the rotation is applied <em>before</em> the translation.
         */
        public final Vector4f rotation = new Vector4f(0, 0, 0, 0);

        /**
         * The mount point's reference name.
         * <br>
         * This is what's used in {@link UpgradeRenderer#computePreferredMountPoint(ItemStack, Robot, Set)}.
         */
        public final String name;

        public MountPoint() {
            name = null;
        }

        public MountPoint(String name) {
            this.name = name;
        }
    }
}
