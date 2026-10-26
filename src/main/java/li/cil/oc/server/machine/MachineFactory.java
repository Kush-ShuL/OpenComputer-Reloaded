package li.cil.oc.server.machine;

import li.cil.oc.api.detail.MachineAPI;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.MachineHost;
import java.util.*;

/** Keeps architecture registration order and validates the public OC constructor. */
public final class MachineFactory implements MachineAPI {
    public static final MachineFactory INSTANCE=new MachineFactory();
    private final LinkedHashSet<Class<? extends Architecture>> architectures=new LinkedHashSet<>();
    private MachineFactory(){}
    public synchronized void add(Class<? extends Architecture> architecture){
        try{architecture.getConstructor(li.cil.oc.api.machine.Machine.class);}
        catch(NoSuchMethodException exception){throw new IllegalArgumentException("Architecture does not have required constructor",exception);}architectures.add(architecture);
    }
    public synchronized Collection<Class<? extends Architecture>> architectures(){return List.copyOf(architectures);}
    public String getArchitectureName(Class<? extends Architecture> architecture){Architecture.Name name=architecture.getAnnotation(Architecture.Name.class);return name==null?architecture.getSimpleName():name.value();}
    public li.cil.oc.api.machine.Machine create(MachineHost host){return new Machine(host);}
}
