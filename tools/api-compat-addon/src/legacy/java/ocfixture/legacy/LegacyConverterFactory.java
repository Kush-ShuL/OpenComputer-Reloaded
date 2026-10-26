package ocfixture.legacy;

import li.cil.oc.api.driver.Converter;

/** This source set is compiled against the preserved old SDK, never the new SDK. */
public final class LegacyConverterFactory {
    private LegacyConverterFactory() {}
    public static Converter create() {
        return (value, output) -> {
            if (value instanceof Marker marker) output.put("legacyBinary", marker.value());
        };
    }
    public static Object payload(String value) { return new Marker(value); }
    private record Marker(String value) {}
}
