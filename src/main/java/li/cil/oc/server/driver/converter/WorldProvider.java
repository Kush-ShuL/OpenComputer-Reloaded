package li.cil.oc.server.driver.converter;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import li.cil.oc.api.driver.Converter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Pinned OC's double-MD5 identity, without exposing the underlying world seed. */
public final class WorldProvider implements Converter {
    /** DimensionType alone has no seed and can be shared by several dimensions. */
    public record Snapshot(long seed, ResourceKey<Level> dimension) {
        public Snapshot { java.util.Objects.requireNonNull(dimension); }
        @Override public String toString() { return "WorldProvider[" + dimension.location() + "]"; }
    }

    @SuppressWarnings("deprecation")
    @Override public void convert(Object value, Map<Object, Object> output) {
        if (!(value instanceof Snapshot provider)) return;
        var key = provider.dimension();
        Integer legacyId = null;
        if (key.equals(Level.OVERWORLD)) legacyId = 0;
        else if (key.equals(Level.NETHER)) legacyId = -1;
        else if (key.equals(Level.END)) legacyId = 1;
        var hasher = Hashing.md5().newHasher().putLong(provider.seed());
        if (legacyId != null) hasher.putInt(legacyId);
        else hasher.putString(key.location().toString(), StandardCharsets.UTF_8);
        output.put("id", UUID.nameUUIDFromBytes(hasher.hash().asBytes()).toString());
        output.put("name", legacyId != null ? key.location().getPath() : key.location().toString());
    }
}
