# Hologram platform in progress

The three Java files below are preserved unchanged from their original workspace paths:

- `src/main/java/li/cil/oc/common/block/entity/HologramBlockEntity.java`
- `src/main/java/li/cil/oc/common/block/HologramBlock.java`
- `src/main/java/li/cil/oc/common/init/Holograms.java`

They were paused when API stability became the priority. They are deliberately outside the main source set: `HologramClient`, `HologramNetworking`, and the voxel renderer/shader backend have not been implemented yet. These files must not be registered or described as a completed projector implementation.

The reusable component remains at `src/main/java/li/cil/oc/server/component/Hologram.java`. Resume from the MightyPirates source pinned in the port's source records; do not replace missing subsystems with stubs.
