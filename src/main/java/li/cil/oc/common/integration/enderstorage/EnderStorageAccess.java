package li.cil.oc.common.integration.enderstorage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Binds verified public EnderStorage/CodeChickenLib APIs without bundling their classes. */
final class EnderStorageAccess {
    final Class<?> tileType;
    final Class<?> tankType;
    private final Method getFrequency;
    private final Method setFrequency;
    private final Method left;
    private final Method middle;
    private final Method right;
    private final Method owner;
    private final Method ownerName;
    private final Method withLeft;
    private final Method withMiddle;
    private final Method withRight;
    private final Method toArray;
    private final Object[] colours;

    EnderStorageAccess() throws ReflectiveOperationException {
        ClassLoader loader = EnderStorageAccess.class.getClassLoader();
        tileType = Class.forName("codechicken.enderstorage.tile.TileFrequencyOwner", false, loader);
        tankType = Class.forName("codechicken.enderstorage.tile.TileEnderTank", false, loader);
        Class<?> frequency = Class.forName("codechicken.enderstorage.api.Frequency", false, loader);
        Class<?> colour = Class.forName("codechicken.lib.colour.EnumColour", false, loader);
        if (!BlockEntity.class.isAssignableFrom(tileType) || !tileType.isAssignableFrom(tankType) || !colour.isEnum()) {
            throw new ClassNotFoundException("Expected EnderStorage block entities and the CodeChickenLib colour enum");
        }
        getFrequency = tileType.getMethod("getFrequency");
        setFrequency = tileType.getMethod("setFreq", frequency);
        left = frequency.getMethod("left");
        middle = frequency.getMethod("middle");
        right = frequency.getMethod("right");
        owner = frequency.getMethod("owner");
        ownerName = frequency.getMethod("ownerName");
        withLeft = frequency.getMethod("withLeft", colour);
        withMiddle = frequency.getMethod("withMiddle", colour);
        withRight = frequency.getMethod("withRight", colour);
        toArray = frequency.getMethod("toArray");
        colours = colour.getEnumConstants();
        if (getFrequency.getReturnType() != frequency || left.getReturnType() != colour
            || middle.getReturnType() != colour || right.getReturnType() != colour || colours.length != 16
            || owner.getReturnType() != Optional.class || ownerName.getReturnType() != Optional.class) {
            throw new NoSuchMethodException("Unsupported EnderStorage frequency or colour API");
        }
    }

    Object frequency(BlockEntity tile) { return invoke(getFrequency, tile); }

    Object[] numbers(Object frequency) {
        return new Object[]{((Enum<?>) invoke(left, frequency)).ordinal(), ((Enum<?>) invoke(middle, frequency)).ordinal(),
            ((Enum<?>) invoke(right, frequency)).ordinal()};
    }

    Object[] frequencyColours(Object frequency) { return (Object[]) invoke(toArray, frequency); }

    Object[] colours() { return colours.clone(); }

    String owner(Object frequency) {
        Optional<?> uuid = (Optional<?>) invoke(owner, frequency);
        if (uuid.isEmpty()) return "global";
        Object value = uuid.get();
        if (!(value instanceof UUID identity)) throw new IllegalStateException("EnderStorage owner is not a UUID");
        Optional<?> name = (Optional<?>) invoke(ownerName, frequency);
        if (name.isPresent()) {
            if (!(name.get() instanceof Component component)) throw new IllegalStateException("EnderStorage ownerName is not a Component");
            return component.getString();
        }
        return identity.toString();
    }

    void setFrequency(BlockEntity tile, int leftColour, int middleColour, int rightColour) {
        Object current = frequency(tile);
        // These public immutable operations preserve both owner UUID and ownerName.
        // Never replace an owned frequency with a public one or assign another owner.
        Object changed = invoke(withLeft, current, colours[leftColour]);
        changed = invoke(withMiddle, changed, colours[middleColour]);
        changed = invoke(withRight, changed, colours[rightColour]);
        // The official setter saves, synchronizes and invalidates the affected capabilities.
        invoke(setFrequency, tile, changed);
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try { return method.invoke(target, arguments); }
        catch (IllegalAccessException exception) { throw new IllegalStateException("EnderStorage public API is inaccessible", exception); }
        catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("EnderStorage public API call failed", cause);
        }
    }
}
