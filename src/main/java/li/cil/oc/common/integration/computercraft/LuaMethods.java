package li.cil.oc.common.integration.computercraft;

import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Binds the documented public LuaFunction contract without accessing CC internals. */
final class LuaMethods {
    static final ILuaContext UNSUPPORTED_CONTEXT=task->{throw new LuaException("ComputerCraft Lua context tasks are not supported by OpenComputers");};
    /** Completes actual CC tasks inline while OC already owns the server-thread callback. */
    private static final class InlineContext implements ILuaContext {
        private long nextId;
        private final ArrayDeque<Object[]> completed=new ArrayDeque<>();
        @Override public long issueMainThreadTask(LuaTask task){
            long id=++nextId;
            try{
                Object[] values=task.execute();Object[] event=new Object[3+(values==null?0:values.length)];
                event[0]="task_complete";event[1]=id;event[2]=true;if(values!=null)System.arraycopy(values,0,event,3,values.length);completed.add(event);
            }catch(LuaException error){completed.add(new Object[]{"task_complete",id,false,error.getMessage()});}
            return id;
        }
        @Override public MethodResult executeMainThreadTask(LuaTask task)throws LuaException{return MethodResult.of(task.execute());}
    }
    private final Object target;
    private final Map<String,Method> annotated=new TreeMap<>();
    private final Map<String,Integer> dynamic=new LinkedHashMap<>();
    LuaMethods(Object target){
        this.target=target;
        if(target instanceof IDynamicPeripheral peripheral){String[] names=peripheral.getMethodNames();for(int i=0;i<names.length;i++)dynamic.put(names[i],i);}
        else if(target instanceof IDynamicLuaObject object){String[] names=object.getMethodNames();for(int i=0;i<names.length;i++)dynamic.put(names[i],i);}
        for(Method method:target.getClass().getMethods()){
            LuaFunction annotation=method.getAnnotation(LuaFunction.class);if(annotation==null||Modifier.isStatic(method.getModifiers()))continue;
            if(!method.trySetAccessible())continue;String[] names=annotation.value();if(names.length==0)annotated.putIfAbsent(method.getName(),method);else for(String name:names)annotated.putIfAbsent(name,method);
        }
    }
    String[] names(){Set<String> names=new TreeSet<>(annotated.keySet());names.addAll(dynamic.keySet());return names.toArray(String[]::new);}
    boolean empty(){return annotated.isEmpty()&&dynamic.isEmpty();}
    static ObjectArguments arguments(li.cil.oc.api.machine.Arguments args){Object[] converted=new Object[args.count()];for(int i=0;i<converted.length;i++){Object value=args.checkAny(i);converted[i]=value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):value;}return new ObjectArguments(converted);}
    Object[] invoke(String name,IComputerAccess computer,IArguments args,ServerLevel level)throws Exception{
        ILuaContext context=level!=null&&level.getServer().isSameThread()?new InlineContext():UNSUPPORTED_CONTEXT;
        if(dynamic.containsKey(name)){
            int index=dynamic.get(name);MethodResult result=target instanceof IDynamicPeripheral peripheral?peripheral.callMethod(computer,context,index,args):((IDynamicLuaObject)target).callMethod(context,index,args);
            return immediate(result,context);
        }
        Method method=annotated.get(name);if(method==null)throw new NoSuchMethodException(name);
        if(method.getAnnotation(LuaFunction.class).mainThread()&&(level==null||!level.getServer().isSameThread()))throw new LuaException("ComputerCraft method requires a synchronized server-thread callback");
        Object[] parameters=new Object[method.getParameterCount()];Type[] types=method.getGenericParameterTypes();int index=0;
        for(int i=0;i<types.length;i++){
            if(types[i]==ILuaContext.class)parameters[i]=context;
            else if(types[i]==IComputerAccess.class){if(computer==null)throw new LuaException("ComputerCraft method requires a computer attachment");parameters[i]=computer;}
            else if(types[i]==IArguments.class)parameters[i]=args.drop(index);
            else parameters[i]=argument(args,index++,types[i]);
        }
        Object result;
        try{result=method.invoke(target,parameters);}catch(InvocationTargetException error){if(error.getCause() instanceof Exception cause)throw cause;throw new RuntimeException(error.getCause());}
        if(method.getReturnType()==void.class)return null;
        if(result instanceof MethodResult methodResult)return immediate(methodResult,context);
        return result instanceof Object[] array?array:new Object[]{result};
    }
    private static Object[] immediate(MethodResult result,ILuaContext context)throws LuaException{
        while(result!=null&&result.getCallback()!=null){
            Object[] filter=result.getResult();
            if(!(context instanceof InlineContext inline)||filter==null||filter.length!=1||!"task_complete".equals(filter[0])||inline.completed.isEmpty())throw new LuaException("ComputerCraft yielding callbacks are not supported by OpenComputers");
            result=result.getCallback().resume(inline.completed.remove());
        }
        return result==null?null:result.getResult();
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static Object argument(IArguments args,int index,Type type)throws LuaException{
        if(type instanceof ParameterizedType parameterized){
            Type raw=parameterized.getRawType(),inner=parameterized.getActualTypeArguments()[0];
            if(raw==Optional.class)return args.get(index)==null?Optional.empty():Optional.ofNullable(argument(args,index,inner));
            if(raw==Coerced.class){if(inner==String.class)return new Coerced<>(args.getStringCoerced(index));if(inner==ByteBuffer.class)return new Coerced<>(args.getBytesCoerced(index));throw new LuaException("unsupported coerced argument type: "+inner.getTypeName());}
            type=raw;
        }
        if(type==String.class)return args.getString(index);
        if(type==double.class||type==Double.class)return args.getDouble(index);
        if(type==int.class||type==Integer.class)return args.getInt(index);
        if(type==long.class||type==Long.class)return args.getLong(index);
        if(type==boolean.class||type==Boolean.class)return args.getBoolean(index);
        if(type==ByteBuffer.class)return args.getBytes(index);
        if(type==Object.class)return args.get(index);
        if(type instanceof Class<?> clazz){if(clazz.isEnum())return args.getEnum(index,(Class)clazz);if(LuaTable.class.isAssignableFrom(clazz))return args.getTableUnsafe(index);if(Map.class.isAssignableFrom(clazz))return args.getTable(index);}
        throw new LuaException("unsupported ComputerCraft argument type: "+type.getTypeName());
    }
}
