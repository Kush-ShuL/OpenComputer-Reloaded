package li.cil.oc.server.machine;

import li.cil.oc.OpenComputers;
import li.cil.oc.api.driver.*;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Callback discovery, whitelist intersection and prioritized driver dispatch. */
public final class Callbacks {
    private Callbacks() {}
    public interface CompositeEnvironment extends Environment { Collection<?> callbackEnvironments(); }
    private record Entry(Object target, Method method, Callback annotation, String peripheralMethod) {
        Object[] invoke(Context context,Object[] args) throws Exception {
            try {
                if(peripheralMethod!=null)return ((ManagedPeripheral)target).invoke(peripheralMethod,context,new ArgumentsImpl(args));
                return (Object[])method.invoke(target,context,new ArgumentsImpl(args));
            }catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof Exception x)throw x;if(cause instanceof Error x)throw x;throw new RuntimeException(cause);}
        }
    }
    private static final Map<Class<?>,Map<String,Method>> CACHE=new ConcurrentHashMap<>();
    public static void clear(){CACHE.clear();}
    private static Map<String,Method> analyze(Class<?> type){
        return CACHE.computeIfAbsent(type,key->{
            Map<String,Method> result=new LinkedHashMap<>();
            for(Class<?> c=key;c!=null&&c!=Object.class;c=c.getSuperclass())for(Method m:c.getDeclaredMethods()){
                Callback a=m.getAnnotation(Callback.class);if(a==null)continue;
                if(!Modifier.isPublic(m.getModifiers())||m.getReturnType()!=Object[].class||!Arrays.equals(m.getParameterTypes(),new Class<?>[]{Context.class,Arguments.class})){
                    OpenComputers.LOGGER.error("Invalid Callback signature {}.{}",c.getName(),m.getName());continue;
                }
                // Addons commonly use non-public environment classes with public callbacks.
                if(!m.trySetAccessible()){
                    OpenComputers.LOGGER.error("Inaccessible Callback {}.{}",c.getName(),m.getName());continue;
                }
                result.putIfAbsent(a.value().isBlank()?m.getName():a.value(),m);
            }
            return Collections.unmodifiableMap(result);
        });
    }
    private static Map<String,Entry> entries(Object host){
        List<Object> hosts=new ArrayList<>(host instanceof CompositeEnvironment multi?multi.callbackEnvironments():List.of(host));
        Set<String> whitelist=null;
        for(Object env:hosts)if(env instanceof MethodWhitelist filter){
            String[] names=filter.whitelistedMethods();Set<String> allowed=new HashSet<>(names==null?List.of():Arrays.asList(names));
            if(whitelist==null)whitelist=allowed;else whitelist.retainAll(allowed);
        }
        hosts.sort(Comparator.comparingInt(e->e instanceof NamedBlock named?-named.priority():0));
        Map<String,Entry> result=new LinkedHashMap<>();
        for(Object env:hosts){
            Set<String> allowed=whitelist;
            java.util.function.Predicate<String> include=n->!result.containsKey(n)&&(allowed==null||allowed.isEmpty()||allowed.contains(n))&&(!(env instanceof FilteredEnvironment f)||f.isCallbackEnabled(n));
            if(env instanceof ManagedPeripheral peripheral)for(String name:peripheral.methods())if(include.test(name))result.put(name,new Entry(env,null,new PeripheralAnnotation(name),name));
            analyze(env.getClass()).forEach((name,method)->{if(include.test(name))result.put(name,new Entry(env,method,method.getAnnotation(Callback.class),null));});
        }
        return result;
    }
    public static Collection<String> methods(Object host){return Collections.unmodifiableSet(entries(host).keySet());}
    public static Callback annotation(Object host,String method){Entry entry=entries(host).get(method);return entry==null?null:entry.annotation();}
    public static Object[] invoke(Object host,String method,Context context,Object[] args)throws Exception{
        Entry entry=entries(host).get(method);if(entry==null)throw new NoSuchMethodException(method);return entry.invoke(context,args);
    }
    private record PeripheralAnnotation(String value) implements Callback {
        public boolean direct(){return false;}
        public int limit(){return Integer.MAX_VALUE;}
        public String doc(){return "";}
        public boolean getter(){return false;}
        public boolean setter(){return false;}
        public Class<? extends Annotation> annotationType(){return Callback.class;}
    }
}
