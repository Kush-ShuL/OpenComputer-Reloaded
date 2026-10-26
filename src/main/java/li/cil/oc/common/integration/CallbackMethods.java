package li.cil.oc.common.integration;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import li.cil.oc.OpenComputers;

/** Static callback resolution shared by the original string-based IMC messages. */
public final class CallbackMethods {
    private CallbackMethods() {}
    public static Method resolve(String name, Class<?>... parameters) throws ReflectiveOperationException {
        int split = name.lastIndexOf('.');
        if (split <= 0 || split == name.length() - 1) throw new IllegalArgumentException("Expected package.Class.method: " + name);
        Class<?> owner = Class.forName(name.substring(0, split), true, Thread.currentThread().getContextClassLoader());
        Method method = owner.getDeclaredMethod(name.substring(split + 1), parameters);
        if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(owner.getModifiers())) throw new IllegalArgumentException("IMC callback must be public static: " + name);
        return method;
    }
    public static Method resolve(String name, Class<?> returns, Class<?>[] parameters) throws ReflectiveOperationException {
        Method method = resolve(name, parameters);
        Class<?> actual = method.getReturnType();
        boolean valid = returns == boolean.class ? actual == boolean.class || actual == Boolean.class
            : returns == int.class ? actual == int.class || actual == Integer.class
            : returns == double.class ? actual == double.class || actual == Double.class : returns.isAssignableFrom(actual);
        if (!valid) throw new IllegalArgumentException("Invalid return type for IMC callback " + name + ": " + actual.getTypeName());
        return method;
    }
    public static Object invoke(Method method, Object fallback, Object... arguments) {
        try { return method.invoke(null, arguments); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError error) { OpenComputers.LOGGER.warn("Error invoking OpenComputers callback {}", method, error); return fallback; }
    }
    public static boolean booleanResult(Method method, boolean fallback, Object... arguments) { Object result = invoke(method, fallback, arguments); return result instanceof Boolean flag ? flag : fallback; }
    public static double doubleResult(Method method, double fallback, Object... arguments) { Object result = invoke(method, fallback, arguments); return result instanceof Number number ? number.doubleValue() : fallback; }
}
