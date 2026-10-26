package li.cil.oc.util;

import java.lang.reflect.Field;

/** Reflection for the named runtime; call sites must use current Mojang names. */
public final class OCObfuscationReflectionHelper {
    private OCObfuscationReflectionHelper() {}
    private static Field field(Class<?> owner,String name) {
        try {Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;}
        catch(ReflectiveOperationException exception){throw new IllegalArgumentException(owner.getName()+"."+name,exception);}
    }
    @SuppressWarnings("unchecked") public static <T,E>T getPrivateValue(Class<? super E> owner,E instance,String name){
        try{return (T)field(owner,name).get(instance);}catch(IllegalAccessException exception){throw new IllegalStateException(exception);}
    }
    public static <T,E>void setPrivateValue(Class<? super T> owner,T instance,E value,String name){
        try{field(owner,name).set(instance,value);}catch(IllegalAccessException exception){throw new IllegalStateException(exception);}
    }
}
