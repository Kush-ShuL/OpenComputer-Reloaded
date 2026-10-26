package li.cil.oc.server.driver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import li.cil.oc.api.Network;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared network identity and persistence for the drivers of one block. */
public final class CompoundBlockEnvironment implements ManagedEnvironment, li.cil.oc.server.machine.Callbacks.CompositeEnvironment {
    public record Entry(String driver, ManagedEnvironment environment) {
        public Entry { Objects.requireNonNull(driver); Objects.requireNonNull(environment); }
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(CompoundBlockEnvironment.class);
    public final String name;
    public final List<Entry> environments;
    public final Component node;
    private final List<ManagedEnvironment> updating;
    private final long typeHash;

    public CompoundBlockEnvironment(String name, List<Entry> entries) {
        this.name = Objects.requireNonNull(name);
        environments = List.copyOf(entries);
        updating = entries.stream().map(Entry::environment).filter(ManagedEnvironment::canUpdate).toList();
        Visibility reachability = Visibility.None;
        for (Entry entry : environments) {
            Node child = entry.environment().node();
            if (child != null && child.reachability().ordinal() > reachability.ordinal()) reachability = child.reachability();
        }
        var builder = Network.newNode(this, reachability);
        if (builder == null) throw new IllegalStateException("OpenComputers network API is not initialized");
        node = builder.withComponent(name).create();
        for (Entry entry : environments)
            if (entry.environment().node() instanceof Component component) component.setVisibility(Visibility.Neighbors);
        typeHash = typeHash(environments);
    }

    @Override public Component node() { return node; }
    @Override public java.util.Collection<? extends li.cil.oc.api.network.Environment> callbackEnvironments() {
        return environments.stream().map(Entry::environment).toList();
    }
    @Override public boolean canUpdate() { return !updating.isEmpty(); }
    @Override public void update() { for (ManagedEnvironment environment : updating) environment.update(); }

    @Override
    public void onConnect(Node connected) {
        if (connected == node)
            for (Entry entry : environments) if (entry.environment().node() != null) node.connect(entry.environment().node());
    }

    @Override
    public void onDisconnect(Node disconnected) {
        if (disconnected == node)
            for (Entry entry : environments) if (entry.environment().node() != null) entry.environment().node().remove();
    }

    @Override public void onMessage(Message message) {
        // Child nodes receive network messages through their real connections.
    }
    @Override public void load(CompoundTag tag) { load(tag, null); }
    @Override public void save(CompoundTag tag) { save(tag, null); }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("typeHash") && tag.getLong("typeHash") != typeHash) return;
        node.load(tag);
        for (Entry entry : environments) {
            if (!tag.contains(entry.driver())) continue;
            try {
                if (registries == null) entry.environment().load(tag.getCompound(entry.driver()));
                else entry.environment().load(tag.getCompound(entry.driver()), registries);
            } catch (RuntimeException error) {
                LOGGER.warn("Block component {} provided by {} failed to load", entry.environment().getClass().getName(), entry.driver(), error);
            }
        }
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("typeHash", typeHash);
        node.save(tag);
        for (Entry entry : environments) {
            try {
                CompoundTag data = new CompoundTag();
                if (registries == null) entry.environment().save(data);
                else entry.environment().save(data, registries);
                tag.put(entry.driver(), data);
            } catch (RuntimeException error) {
                LOGGER.warn("Block component {} provided by {} failed to save", entry.environment().getClass().getName(), entry.driver(), error);
            }
        }
    }

    private static long typeHash(List<Entry> environments) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            environments.stream().map(entry -> entry.environment().getClass().getName()).sorted()
                    .forEach(type -> digest.update(type.getBytes(StandardCharsets.UTF_8)));
            return ByteBuffer.wrap(digest.digest()).order(ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
}
