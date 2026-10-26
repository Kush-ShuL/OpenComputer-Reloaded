package li.cil.oc.server.component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.CRC32;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.nbt.CompoundTag;

/** Persistent BIOS flash and writable user data, with the original energy/delay rules. */
public final class EEPROM extends AbstractManagedEnvironment implements DeviceInfo {
    private byte[] code = new byte[0], data = new byte[0];
    private boolean readonly;
    private String label = "EEPROM";

    public EEPROM() {
        setNode(Network.newNode(this, Visibility.Neighbors).withComponent("eeprom", Visibility.Neighbors).withConnector().create());
    }
    private ComponentConnector connector() { return (ComponentConnector) node(); }
    public synchronized String checksum() {
        CRC32 crc = new CRC32(); crc.update(code);
        return HexFormat.of().formatHex(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) crc.getValue()).array());
    }
    @Override public Map<String, String> getDeviceInfo() {
        return Map.of("class", "memory", "description", "EEPROM", "vendor", "MightyPirates GmbH & Co. KG",
                "product", "FlashStick2k", "capacity", Integer.toString(Settings.get().eepromSize), "size", Integer.toString(Settings.get().eepromSize));
    }
    @Callback(direct=true, doc="function():string -- Get the currently stored byte array.")
    public synchronized Object[] get(Context context, Arguments args) { return new Object[]{code.clone()}; }
    @Callback(doc="function(data:string) -- Overwrite the currently stored byte array.")
    public synchronized Object[] set(Context context, Arguments args) {
        if (readonly) return new Object[]{null, "storage is readonly"};
        if (!connector().tryChangeBuffer(-Settings.get().eepromWriteCost)) return new Object[]{null, "not enough energy"};
        byte[] value = args.optByteArray(0, new byte[0]);
        if (value.length > Settings.get().eepromSize) throw new IllegalArgumentException("not enough space");
        code = value.clone(); context.pause(2); return null;
    }
    @Callback(direct=true, doc="function():string -- Get the label of the EEPROM.")
    public synchronized Object[] getLabel(Context context, Arguments args) { return new Object[]{label}; }
    @Callback(doc="function(data:string):string -- Set the label of the EEPROM.")
    public synchronized Object[] setLabel(Context context, Arguments args) {
        if (readonly) return new Object[]{null, "storage is readonly"};
        String value = args.optString(0, "EEPROM").trim(); label = value.substring(0, Math.min(24, value.length()));
        if (label.isEmpty()) label = "EEPROM";
        return new Object[]{label};
    }
    @Callback(direct=true, doc="function():number -- Get the code storage capacity.")
    public Object[] getSize(Context context, Arguments args) { return new Object[]{Settings.get().eepromSize}; }
    @Callback(direct=true, doc="function():string -- Get the code checksum.")
    public synchronized Object[] getChecksum(Context context, Arguments args) { return new Object[]{checksum()}; }
    @Callback(direct=true, doc="function(checksum:string):boolean -- Permanently make the code and label read-only.")
    public synchronized Object[] makeReadonly(Context context, Arguments args) {
        if (!checksum().equals(args.checkString(0))) return new Object[]{null, "incorrect checksum"};
        readonly = true; return new Object[]{true};
    }
    @Callback(direct=true, doc="function():number -- Get writable user data capacity.")
    public Object[] getDataSize(Context context, Arguments args) { return new Object[]{Settings.get().eepromDataSize}; }
    @Callback(direct=true, doc="function():string -- Get the user data byte array.")
    public synchronized Object[] getData(Context context, Arguments args) { return new Object[]{data.clone()}; }
    @Callback(doc="function(data:string) -- Replace the user data byte array.")
    public synchronized Object[] setData(Context context, Arguments args) {
        if (!connector().tryChangeBuffer(-Settings.get().eepromWriteCost)) return new Object[]{null, "not enough energy"};
        byte[] value = args.optByteArray(0, new byte[0]);
        if (value.length > Settings.get().eepromDataSize) throw new IllegalArgumentException("not enough space");
        data = value.clone(); context.pause(1); return null;
    }
    @Override public synchronized void load(CompoundTag tag) {
        super.load(tag); code = tag.getByteArray("oc:eeprom"); data = tag.getByteArray("oc:userdata");
        readonly = tag.getBoolean("oc:readonly"); if (tag.contains("oc:label")) label = tag.getString("oc:label");
    }
    @Override public synchronized void save(CompoundTag tag) {
        super.save(tag); tag.putByteArray("oc:eeprom", code); tag.putByteArray("oc:userdata", data);
        tag.putBoolean("oc:readonly", readonly); tag.putString("oc:label", label);
    }
}
