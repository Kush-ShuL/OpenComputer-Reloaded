package ocfixture;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Test orchestration only. Device traffic always uses the production payloads. */
public final class MultiplayerProtocol {
    static final int FAMILY_FIRST = 100;
    static Consumer<Step> receiver = step -> {};
    private MultiplayerProtocol() {}
    public record Step(int id, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Step> TYPE = new Type<>(ResourceLocation.parse("oc_api_compat:tcp_step"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Step> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeVarInt(value.id); buf.writeNbt(value.data); },
            buf -> new Step(buf.readVarInt(), java.util.Objects.requireNonNull(buf.readNbt())));
        public Type<Step> type() { return TYPE; }
    }
    public record Ack(int id, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Ack> TYPE = new Type<>(ResourceLocation.parse("oc_api_compat:tcp_ack"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ack> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeVarInt(value.id); buf.writeNbt(value.data); },
            buf -> new Ack(buf.readVarInt(), java.util.Objects.requireNonNull(buf.readNbt())));
        public Type<Ack> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Step.TYPE, Step.CODEC, (packet, context) -> context.enqueueWork(() -> receiver.accept(packet)));
        registrar.playToServer(Ack.TYPE, Ack.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) MultiplayerServerContract.ack(player, packet);
        }));
    }
    static Object call(Object host, String name, Object... values) {
        Class<?> type = host instanceof Class<?> c ? c : host.getClass();
        for (Method method : type.getMethods()) if (method.getName().equals(name) && matches(method.getParameterTypes(), values)) {
            try { return method.invoke(host instanceof Class<?> ? null : host, values); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(type.getName() + "." + name, error); }
        }
        throw new IllegalArgumentException("Missing actual method " + type.getName() + "." + name);
    }
    static Object type(String name) { try { return Class.forName(name); } catch (ClassNotFoundException error) { throw new IllegalStateException(error); } }
    static Object field(Object host, String name) {
        Class<?> type = host instanceof Class<?> c ? c : host.getClass();
        while (type != null) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(host instanceof Class<?> ? null : host); }
            catch (NoSuchFieldException error) { type = type.getSuperclass(); }
            catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        throw new IllegalArgumentException("Missing actual field " + name);
    }
    static CustomPacketPayload payload(String owner, String record, Object... values) {
        Class<?> type = (Class<?>) type("li.cil.oc.common.network." + owner + "$" + record);
        for (var constructor : type.getConstructors()) if (matches(constructor.getParameterTypes(), values)) {
            try { return (CustomPacketPayload) constructor.newInstance(values); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        throw new IllegalArgumentException("Missing actual payload constructor " + type.getName());
    }
    private static boolean matches(Class<?>[] parameters, Object[] values) {
        if (parameters.length != values.length) return false;
        for (int i = 0; i < parameters.length; i++) {
            Class<?> parameter = parameters[i];
            if (parameter.isPrimitive()) parameter = parameter == int.class ? Integer.class : parameter == boolean.class ? Boolean.class : parameter == long.class ? Long.class : parameter == double.class ? Double.class : parameter == float.class ? Float.class : parameter == char.class ? Character.class : parameter;
            if (values[i] != null && !parameter.isInstance(values[i])) return false;
        }
        return true;
    }
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
