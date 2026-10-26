package li.cil.oc.server.network;

import li.cil.oc.Settings;
import li.cil.oc.api.network.Packet;
import net.minecraft.nbt.*;

public final class NetworkPacket implements Packet {
    private final String source, destination;
    private final int port, ttl, size;
    private final Object[] data;
    public NetworkPacket(String source, String destination, int port, Object[] data) { this(source, destination, port, data, Settings.get().initialNetworkPacketTTL); }
    private NetworkPacket(String source, String destination, int port, Object[] data, int ttl) {
        this.source = source; this.destination = destination; this.port = port; this.ttl = ttl; this.data = data == null ? new Object[0] : data;
        if (this.data.length > Settings.get().maxNetworkPacketParts) throw new IllegalArgumentException("packet has too many parts");
        int result = this.data.length * 2;
        for (Object value : this.data) result += switch (value) {
            case null -> 1;
            case Boolean ignored -> 1;
            case Byte ignored -> 2;
            case Short ignored -> 2;
            case Integer ignored -> 4;
            case Long ignored -> 8;
            case Float ignored -> 4;
            case Double ignored -> 8;
            case String text -> Math.max(1, text.length());
            case byte[] bytes -> Math.max(1, bytes.length);
            default -> throw new IllegalArgumentException("unsupported data type: " + value.getClass().getName());
        };
        size = result;
    }
    public static NetworkPacket load(CompoundTag nbt) {
        int count = nbt.getInt("dataLength");
        if (count < 0 || count > Settings.get().maxNetworkPacketParts) throw new IllegalArgumentException("invalid packet data length");
        Object[] data = new Object[count];
        for (int index = 0; index < count; index++) data[index] = switch (nbt.get("data" + index)) {
            case ByteTag value -> value.getAsByte() == 1;
            case ShortTag value -> value.getAsShort();
            case IntTag value -> value.getAsInt();
            case LongTag value -> value.getAsLong();
            case FloatTag value -> value.getAsFloat();
            case DoubleTag value -> value.getAsDouble();
            case StringTag value -> value.getAsString();
            case ByteArrayTag value -> value.getAsByteArray();
            default -> null;
        };
        return new NetworkPacket(nbt.getString("source"), nbt.contains("dest") ? nbt.getString("dest") : null, nbt.getInt("port"), data, nbt.getInt("ttl"));
    }
    public String source() { return source; }
    public String destination() { return destination; }
    public int port() { return port; }
    public Object[] data() { return data; }
    public int size() { return size; }
    public int ttl() { return ttl; }
    public Packet hop() { return new NetworkPacket(source, destination, port, data, ttl - 1); }
    public void save(CompoundTag nbt) {
        nbt.putString("source", source);
        if (destination != null && !destination.isEmpty()) nbt.putString("dest", destination); else nbt.remove("dest");
        nbt.putInt("port", port); nbt.putInt("ttl", ttl); nbt.putInt("dataLength", data.length);
        for (int index = 0; index < data.length; index++) {
            String key = "data" + index;
            switch (data[index]) {
                case null -> nbt.remove(key);
                case Boolean value -> nbt.putBoolean(key, value);
                case Byte value -> nbt.putShort(key, value.shortValue());
                case Short value -> nbt.putShort(key, value);
                case Integer value -> nbt.putInt(key, value);
                case Long value -> nbt.putLong(key, value);
                case Float value -> nbt.putFloat(key, value);
                case Double value -> nbt.putDouble(key, value);
                case String value -> nbt.putString(key, value);
                case byte[] value -> nbt.putByteArray(key, value);
                default -> throw new IllegalArgumentException("unsupported packet data");
            }
        }
    }
}
