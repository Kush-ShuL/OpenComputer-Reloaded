package li.cil.oc.common.integration.forge;

import li.cil.oc.Settings;
import li.cil.oc.common.capabilities.ExternalPower;
import li.cil.oc.common.integration.ItemCharge;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Original MinecraftForge item-charge hook, migrated from MightyPirates OC
 * 258943363ea8fc4ef14948e6bc6f67375061580c (MIT, see LICENSE and NOTICE).
 * The ratio is OC energy per FE, matching upstream Power.fromRF/toRF.
 */
public final class ForgeEnergyItemCharge {
    private static final ItemCharge.Handler HANDLER = new ItemCharge.Handler("MinecraftForge",
        ForgeEnergyItemCharge::canCharge, ForgeEnergyItemCharge::charge);

    private ForgeEnergyItemCharge() {}

    public static void register() { ItemCharge.add(HANDLER); }

    private static IEnergyStorage storage(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ExternalPower.forgeEnergyEnabled()) return null;
        return stack.getCapability(Capabilities.EnergyStorage.ITEM);
    }

    public static boolean canCharge(ItemStack stack) {
        double ratio = Settings.get().ratioForgeEnergy;
        if (!(ratio > 0) || !Double.isFinite(ratio)) return false;
        IEnergyStorage storage = storage(stack);
        return storage != null && storage.canReceive();
    }

    /** Returns unapplied OC energy, including fractional FE which cannot be sent. */
    public static double charge(ItemStack stack, double amount, boolean simulate) {
        // Never pass negative/NaN/infinite energy or zero-sized offers to a provider.
        if (!(amount > 0) || !Double.isFinite(amount)) return amount;
        double ratio = Settings.get().ratioForgeEnergy;
        if (!(ratio > 0) || !Double.isFinite(ratio)) return amount;
        IEnergyStorage storage = storage(stack);
        if (storage == null || !storage.canReceive()) return amount;
        // FE accepts an int. Positive narrowing truncates fractional FE as upstream
        // did; saturate oversized offers rather than overflowing through a long.
        int offered = (int) Math.min(Integer.MAX_VALUE, amount / ratio);
        if (offered <= 0) return amount;
        int accepted = Math.clamp(storage.receiveEnergy(offered, simulate), 0, offered);
        // Forward simulation unchanged; only the provider owns item energy state.
        // Bound floating-point rounding and a misbehaving provider's return value.
        return amount - Math.min(amount, accepted * ratio);
    }
}
