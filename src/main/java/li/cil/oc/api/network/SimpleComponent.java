package li.cil.oc.api.network;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a block entity whose public callback methods should be exposed as a
 * component. Annotate methods with {@link li.cil.oc.api.machine.Callback}, or
 * additionally implement {@link ManagedPeripheral} to dispatch methods by name.
 *
 * <p>The NeoForge integration must wrap this marker in a managed environment;
 * the legacy Forge class transformer and Optional annotations are not used.
 * Implement {@link Environment} or extend an environment prefab when direct
 * access to the node, connectivity, messages, or power is required.</p>
 *
 * <p>Keep optional OpenComputers integration classes separate from classes
 * loaded when OpenComputers is absent. Do not embed copies of this API.</p>
 */
public interface SimpleComponent {
    /** The lowercase component name exposed by component.list(), e.g. fancy_thing. */
    String getComponentName();

    /**
     * Prevent automatic wrapping of template classes. The historical annotation
     * name is retained for source compatibility; no bytecode injection occurs.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface SkipInjection {
    }
}
