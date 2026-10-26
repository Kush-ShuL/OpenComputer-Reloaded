package li.cil.oc.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;

/**
 * An object that can be persisted to an NBT tag and restored back from it.
 */
public interface Persistable {
    /**
     * Restores a previous state of the object from the specified NBT tag.
     *
     * @param nbt the tag to read the state from.
     */
    void load(CompoundTag nbt);

    /**
     * Saves the current state of the object into the specified NBT tag.
     * <br>
     * This should write the state in such a way that it can be restored when
     * {@link #load} is called with that tag.
     *
     * @param nbt the tag to save the state to.
     */
    void save(CompoundTag nbt);

    /**
     * Registry-aware load for state containing item stacks or other data components.
     * The host must supply its level's registry access.
     */
    default void load(CompoundTag nbt, HolderLookup.Provider registries) {
        load(nbt);
    }

    /**
     * Registry-aware save. Hosts must call this overload for environments and
     * values containing item stacks, including ItemStackArrayValue.
     */
    default void save(CompoundTag nbt, HolderLookup.Provider registries) {
        save(nbt);
    }
}