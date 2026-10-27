package li.cil.oc.common.integration.tis3d;

import java.util.ArrayDeque;
import java.util.Optional;
import li.cil.oc.api.Network;
import li.cil.oc.api.internal.Adapter;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.tis3d.api.serial.SerialInterface;
import li.cil.tis3d.api.serial.SerialInterfaceProvider;
import li.cil.tis3d.api.serial.SerialProtocolDocumentationReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Java migration of the original MIT TIS-3D integration in MightyPirates/OpenComputers
 * 258943363ea8fc4ef14948e6bc6f67375061580c. See LICENSE and NOTICE.
 */
public final class SerialInterfaceProviderAdapter implements SerialInterfaceProvider {
    private static final SerialProtocolDocumentationReference DOCUMENTATION = new SerialProtocolDocumentationReference(
            Component.translatable("block.opencomputers.adapter"), "opencomputersadapter.md");

    @Override public boolean matches(Level level, BlockPos position, Direction side) {
        return level.getBlockEntity(position) instanceof Adapter;
    }

    @Override public Optional<SerialInterface> getInterface(Level level, BlockPos position, Direction side) {
        return level.getBlockEntity(position) instanceof Adapter adapter
                ? Optional.of(new SerialInterfaceAdapter(adapter)) : Optional.empty();
    }

    @Override public Optional<SerialProtocolDocumentationReference> getDocumentationReference() { return Optional.of(DOCUMENTATION); }

    @Override public boolean stillValid(Level level, BlockPos position, Direction side, SerialInterface serialInterface) {
        return serialInterface instanceof SerialInterfaceAdapter adapter && adapter.tileEntity == level.getBlockEntity(position);
    }

    /** TIS-3D controls the handshake; OC supplies the software-defined signed-16-bit protocol. */
    public static final class SerialInterfaceAdapter implements Environment, SerialInterface {
        private static final int BUFFER_CAPACITY = 128;
        private final Adapter tileEntity;
        private final Node node;
        private final ArrayDeque<Short> readBuffer = new ArrayDeque<>();
        private final ArrayDeque<Short> writeBuffer = new ArrayDeque<>();
        private volatile boolean reading;

        public SerialInterfaceAdapter(Adapter tileEntity) {
            this.tileEntity = tileEntity;
            node = Network.newNode(this, Visibility.Network).withComponent("serial_port").create();
        }

        @Override public Node node() { return node; }
        @Override public void onConnect(Node node) {}
        @Override public void onDisconnect(Node node) {}
        @Override public void onMessage(Message message) {}

        @Callback(doc = "function(enabled:boolean) -- Enable or disable receiving values from TIS-3D.")
        public Object[] setReading(Context context, Arguments args) { reading = args.checkBoolean(0); return null; }

        @Callback(doc = "function():number or nil -- Consume the oldest value received from TIS-3D.")
        public Object[] read(Context context, Arguments args) {
            synchronized (readBuffer) {
                Short value = readBuffer.pollFirst();
                return value == null ? null : new Object[]{value};
            }
        }

        @Callback(doc = "function(value:number):boolean[,string] -- Queue a signed 16-bit value for TIS-3D.")
        public Object[] write(Context context, Arguments args) {
            synchronized (writeBuffer) {
                if (writeBuffer.size() >= BUFFER_CAPACITY) return new Object[]{false, "buffer full"};
                writeBuffer.addLast((short) args.checkInteger(0));
                return new Object[]{true};
            }
        }

        @Override public boolean canWrite() {
            synchronized (readBuffer) { return reading && readBuffer.size() < BUFFER_CAPACITY; }
        }

        @Override public void write(short value) {
            // A value already accepted by the TIS-3D handshake must not fail to enter the queue.
            synchronized (readBuffer) { readBuffer.addLast(value); }
        }

        @Override public boolean canRead() {
            ensureConnected();
            synchronized (writeBuffer) { return !writeBuffer.isEmpty(); }
        }

        @Override public short peek() { synchronized (writeBuffer) { return writeBuffer.getFirst(); } }
        @Override public void skip() { synchronized (writeBuffer) { writeBuffer.removeFirst(); } }

        @Override public void reset() {
            synchronized (readBuffer) {
                synchronized (writeBuffer) {
                    readBuffer.clear(); writeBuffer.clear(); node.remove();
                }
            }
        }

        @Override public void load(CompoundTag tag) {
            synchronized (readBuffer) {
                synchronized (writeBuffer) {
                    node.load(tag);
                    writeBuffer.clear(); for (int value : tag.getIntArray("writeBuffer")) writeBuffer.addLast((short) value);
                    readBuffer.clear(); for (int value : tag.getIntArray("readBuffer")) readBuffer.addLast((short) value);
                    reading = tag.getBoolean("isReading");
                }
            }
        }

        @Override public void save(CompoundTag tag) {
            synchronized (readBuffer) {
                synchronized (writeBuffer) {
                    node.save(tag);
                    tag.putIntArray("writeBuffer", writeBuffer.stream().mapToInt(Short::intValue).toArray());
                    tag.putIntArray("readBuffer", readBuffer.stream().mapToInt(Short::intValue).toArray());
                    tag.putBoolean("isReading", reading);
                }
            }
        }

        private void ensureConnected() {
            Node adapterNode = tileEntity.node();
            if (adapterNode.network() != node.network()) adapterNode.connect(node);
        }
    }
}
