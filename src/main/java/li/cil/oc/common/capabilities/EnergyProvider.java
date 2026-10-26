package li.cil.oc.common.capabilities;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** Block entities which expose an original external-power input to NeoForge. */
public interface EnergyProvider {
    @Nullable IEnergyStorage energy(@Nullable Direction side);
}
