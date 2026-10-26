package li.cil.oc.server.component;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.prefab.AbstractValue;
import net.minecraft.nbt.CompoundTag;
import java.io.IOException;
/** Kept under its original binary name so persisted userdata can be restored. */
public final class HandleValue extends AbstractValue {
    private String owner="";
    int handle;
    public HandleValue(){}
    public HandleValue(String owner,int handle){this.owner=owner;this.handle=handle;}
    public void dispose(Context context){if(context.node()==null||context.node().network()==null)return;Node node=context.node().network().node(owner);if(node!=null&&node.host() instanceof FileSystem fileSystem)try{fileSystem.close(context,handle);}catch(IOException ignored){}}
    public void load(CompoundTag tag){owner=tag.getString("owner");handle=tag.getInt("handle");}
    public void save(CompoundTag tag){tag.putString("owner",owner);tag.putInt("handle",handle);}
    public String toString(){return Integer.toString(handle);}
}
