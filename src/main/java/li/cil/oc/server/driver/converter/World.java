package li.cil.oc.server.driver.converter;

import java.util.Map;
import li.cil.oc.api.driver.Converter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;

/** Original World -> WorldProvider flattening, with a world-bound modern snapshot. */
public final class World implements Converter {
    @Override public void convert(Object value, Map<Object, Object> output) {
        if (value instanceof Level level) {
            // Original remote WorldSettings used seed zero. A biome hash is not a world seed.
            long seed = level instanceof WorldGenLevel generated ? generated.getSeed() : 0;
            output.put("oc:flatten", new WorldProvider.Snapshot(seed, level.dimension()));
        }
    }
}
