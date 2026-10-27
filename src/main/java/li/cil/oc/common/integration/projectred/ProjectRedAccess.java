package li.cil.oc.common.integration.projectred;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Binds only the verified public ProjectRed API, without embedding foreign types. */
final class ProjectRedAccess {
    final Class<?> interactionType;
    private final Object transmission;
    private final Class<?> screwdriverType;
    private final Method registerInteraction;
    private final Method getBundledInput;
    private final Method canUse;
    private final Method damageScrewdriver;

    ProjectRedAccess() throws ReflectiveOperationException {
        ClassLoader loader = ProjectRedAccess.class.getClassLoader();
        Class<?> api = Class.forName("mrtjp.projectred.api.ProjectRedAPI", false, loader);
        Class<?> transmissionType = Class.forName("mrtjp.projectred.api.ITransmissionAPI", false, loader);
        interactionType = Class.forName("mrtjp.projectred.api.IBundledTileInteraction", false, loader);
        screwdriverType = Class.forName("mrtjp.projectred.api.IScrewdriver", false, loader);
        if (!transmissionType.isInterface() || !interactionType.isInterface() || !screwdriverType.isInterface())
            throw new ClassNotFoundException("Expected the public ProjectRed transmission/tool interfaces");
        transmission = api.getField("transmissionAPI").get(null);
        if (transmission == null || !transmissionType.isInstance(transmission))
            throw new NoSuchFieldException("ProjectRedAPI.transmissionAPI is unavailable");

        registerInteraction = transmissionType.getMethod("registerBundledTileInteraction", interactionType);
        getBundledInput = transmissionType.getMethod("getBundledInput", Level.class, BlockPos.class, Direction.class);
        canUse = screwdriverType.getMethod("canUse", Player.class, InteractionHand.class);
        damageScrewdriver = screwdriverType.getMethod("damageScrewdriver", Player.class, InteractionHand.class);
        Method valid = interactionType.getMethod("isValidInteractionFor", Level.class, BlockPos.class, Direction.class);
        Method connect = interactionType.getMethod("canConnectBundled", Level.class, BlockPos.class, Direction.class);
        Method signal = interactionType.getMethod("getBundledSignal", Level.class, BlockPos.class, Direction.class);
        if (registerInteraction.getReturnType() != void.class || getBundledInput.getReturnType() != byte[].class
            || canUse.getReturnType() != boolean.class || damageScrewdriver.getReturnType() != void.class
            || valid.getReturnType() != boolean.class || connect.getReturnType() != boolean.class || signal.getReturnType() != byte[].class)
            throw new NoSuchMethodException("Unsupported ProjectRed transmission/tool API signatures");
    }

    void register(Object interaction) { invoke(registerInteraction, transmission, interaction); }

    byte[] input(Level level, BlockPos position, Direction side) {
        // ProjectRed expects the querying host position and its outward side.
        return (byte[]) invoke(getBundledInput, transmission, level, position, side);
    }

    boolean isScrewdriver(ItemStack stack) {
        return stack != null && !stack.isEmpty() && screwdriverType.isInstance(stack.getItem());
    }

    boolean useScrewdriver(Player player, boolean changeDurability) {
        if (player == null) return false;
        ItemStack stack = player.getMainHandItem();
        if (!isScrewdriver(stack) || !Boolean.TRUE.equals(invoke(canUse, stack.getItem(), player, InteractionHand.MAIN_HAND))) return false;
        if (changeDurability) invoke(damageScrewdriver, stack.getItem(), player, InteractionHand.MAIN_HAND);
        return true;
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try { return method.invoke(target, arguments); }
        catch (IllegalAccessException exception) { throw new IllegalStateException("ProjectRed public API is inaccessible", exception); }
        catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("ProjectRed public API call failed", cause);
        }
    }
}
