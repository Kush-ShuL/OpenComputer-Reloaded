package li.cil.oc.common.integration.appeng;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import appeng.api.networking.crafting.ICraftingLink;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.prefab.AbstractValue;
import net.minecraft.nbt.CompoundTag;

/** Persisted crafting request status, paired to the real AE link by its UUID. */
public final class CraftingStatus extends AbstractValue {
    private static final Map<UUID, WeakReference<ICraftingLink>> LINKS = new HashMap<>();
    private static final Map<UUID, WeakReference<CraftingStatus>> WAITING = new HashMap<>();
    private volatile boolean computing = true;
    private volatile ICraftingLink link;
    private UUID pendingId;
    private volatile boolean failed;
    private volatile String reason = "no link";
    public CraftingStatus() {}
    static void clearCache() { LINKS.clear(); WAITING.clear(); }
    static void restore(ICraftingLink link) {
        UUID id = link.getCraftingID();
        var waiting = WAITING.remove(id);
        CraftingStatus status = waiting == null ? null : waiting.get();
        if (status != null) status.attach(link); else LINKS.put(id, new WeakReference<>(link));
    }
    void attach(ICraftingLink value) { link = value; pendingId = value.getCraftingID(); computing = false; failed = false; }
    void fail(String message) { reason = "request failed (" + message + ")"; failed = true; computing = false; }
    private Object[] unavailable() {
        if (computing) return new Object[]{null, "computing"};
        if (failed || link == null) return new Object[]{false, reason};
        return null;
    }
    @Callback(doc="function():boolean -- Get whether the crafting request has been canceled.")
    public Object[] isCanceled(Context context, Arguments args) { var unavailable = unavailable(); return unavailable != null ? unavailable : new Object[]{link.isCanceled()}; }
    @Callback(doc="function():boolean -- Get whether the crafting request is done.")
    public Object[] isDone(Context context, Arguments args) { var unavailable = unavailable(); return unavailable != null ? unavailable : new Object[]{link.isDone()}; }
    @Callback(doc="function():boolean -- Cancel the request, or return nil while its plan is computing.")
    public Object[] cancel(Context context, Arguments args) {
        var unavailable = unavailable(); if (unavailable != null) return unavailable;
        if (link.isDone()) return new Object[]{false, "job already completed"};
        link.cancel(); return new Object[]{true};
    }
    @Override public void save(CompoundTag tag) {
        tag.putBoolean("computing", computing); tag.putBoolean("failed", failed); tag.putString("reason", reason);
        if (link != null) tag.putUUID("link", link.getCraftingID()); else if (pendingId != null) tag.putUUID("link", pendingId);
    }
    @Override public void load(CompoundTag tag) {
        computing = tag.getBoolean("computing"); failed = tag.getBoolean("failed"); reason = tag.getString("reason"); link = null; pendingId = null;
        if (tag.hasUUID("link")) {
            UUID id = tag.getUUID("link"); pendingId = id; var saved = LINKS.remove(id); ICraftingLink existing = saved == null ? null : saved.get();
            if (existing != null) attach(existing); else WAITING.put(id, new WeakReference<>(this));
        } else if (computing) fail("calculation was interrupted by reload");
    }
}
