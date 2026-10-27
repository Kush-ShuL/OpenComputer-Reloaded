package li.cil.oc.common.capabilities;

import li.cil.oc.Settings;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import java.util.Locale;
import java.util.function.Supplier;

/** Controls only OC's external FE boundary, independently of its internal connector network. */
public final class ExternalPower {
    private ExternalPower() {}

    public static boolean forgeEnergyEnabled() {
        if (!li.cil.oc.common.integration.VanillaDrivers.integrationEnabled("forge", "neoforge")) return false;
        for (String entry : Settings.get().powerModBlacklist) {
            switch (entry.trim().toLowerCase(Locale.ROOT)) {
                case "forge", "neoforge", "forgeenergy", "neoforgeenergy" -> { return false; }
            }
        }
        return true;
    }

    /** Do not create a capability when disabled, including lazy node/VM creation in providers. */
    public static @Nullable IEnergyStorage expose(Supplier<IEnergyStorage> factory) {
        if (!forgeEnergyEnabled()) return null;
        IEnergyStorage storage = factory.get();
        return storage == null ? null : new GuardedStorage(storage);
    }

    // A previously cached capability must obey the same policy as a fresh lookup.
    private record GuardedStorage(IEnergyStorage delegate) implements IEnergyStorage {
        @Override public int receiveEnergy(int maximum, boolean simulate) { return forgeEnergyEnabled() ? delegate.receiveEnergy(maximum, simulate) : 0; }
        @Override public int extractEnergy(int maximum, boolean simulate) { return forgeEnergyEnabled() ? delegate.extractEnergy(maximum, simulate) : 0; }
        @Override public int getEnergyStored() { return forgeEnergyEnabled() ? delegate.getEnergyStored() : 0; }
        @Override public int getMaxEnergyStored() { return forgeEnergyEnabled() ? delegate.getMaxEnergyStored() : 0; }
        @Override public boolean canExtract() { return forgeEnergyEnabled() && delegate.canExtract(); }
        @Override public boolean canReceive() { return forgeEnergyEnabled() && delegate.canReceive(); }
    }
}
