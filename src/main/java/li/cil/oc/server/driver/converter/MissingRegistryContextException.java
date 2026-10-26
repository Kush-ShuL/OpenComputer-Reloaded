package li.cil.oc.server.driver.converter;

/** Internal conversion failure which must reach the caller instead of losing metadata. */
public final class MissingRegistryContextException extends IllegalStateException {
    public MissingRegistryContextException(String message) { super(message); }
    public MissingRegistryContextException(String message, Throwable cause) { super(message, cause); }
}
