package li.cil.oc.server.component;

import li.cil.oc.api.internal.Tablet;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;

public final class UpgradeBarcodeReader extends UpgradeBase {
    public UpgradeBarcodeReader(EnvironmentHost host){super(host,"barcode_reader","Barcode reader upgrade","Readerizer Deluxe",true);}
    @Override public void onMessage(Message message){if(!message.name().equals("tablet.use")||!(message.source().host() instanceof Machine machine)||!(machine.host() instanceof Tablet))return;Object[] data=message.data();if(data.length<8||!(data[0] instanceof CompoundTag result)||!(data[2] instanceof Player player)||!(data[3] instanceof BlockPos pos)||!(data[4] instanceof Direction side))return;var entity=host.world().getBlockEntity(pos);Node[] nodes=entity instanceof Analyzable analyzable?analyzable.onAnalyze(player,side,((Number)data[5]).floatValue(),((Number)data[6]).floatValue(),((Number)data[7]).floatValue()):entity instanceof SidedEnvironment sided?new Node[]{sided.sidedNode(side)}:entity instanceof Environment environment?new Node[]{environment.node()}:null;if(nodes==null)return;ListTag analyzed=new ListTag();for(Node node:nodes)if(node!=null){CompoundTag value=new CompoundTag();if(node instanceof Component component)value.putString("type",component.name());if(node.address()!=null&&!node.address().isEmpty())value.putString("address",node.address());analyzed.add(value);}result.put("analyzed",analyzed);}
}
