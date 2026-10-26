package li.cil.oc.server.component;

import li.cil.oc.api.event.SignChangeEvent;
import li.cil.oc.api.internal.Rotatable;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class UpgradeSign extends UpgradeBase {
    public UpgradeSign(EnvironmentHost host){super(host,"sign","Sign upgrade","Labelizer Deluxe",true);}
    private Direction direction(Arguments args){return host instanceof Rotatable rotatable?rotatable.facing():side(args,0);}
    private SignBlockEntity sign(Direction side){if(host.world().getBlockEntity(position()) instanceof SignBlockEntity sign)return sign;return host.world().getBlockEntity(position().relative(side)) instanceof SignBlockEntity sign?sign:null;}
    private String text(SignBlockEntity sign){return Arrays.stream(sign.getFrontText().getMessages(false)).map(Component::getString).collect(Collectors.joining("\n"));}
    @Callback public Object[] getValue(Context context,Arguments args){SignBlockEntity sign=sign(direction(args));return sign==null?new Object[]{null,"no sign"}:new Object[]{text(sign)};}
    @Callback public Object[] setValue(Context context,Arguments args){SignBlockEntity sign=sign(direction(args));if(sign==null)return new Object[]{null,"no sign"};var player=fakePlayer();BlockPos pos=sign.getBlockPos();if(sign.isWaxed()||!host.world().mayInteract(player,pos))return new Object[]{null,"not allowed"};var breaking=new BlockEvent.BreakEvent(host.world(),pos,sign.getBlockState(),player);NeoForge.EVENT_BUS.post(breaking);if(breaking.isCanceled())return new Object[]{null,"not allowed"};String[] source=args.checkString(host instanceof Rotatable?0:1).split("\n",-1),lines=new String[4];for(int i=0;i<4;i++){String line=i<source.length?source[i]:"";lines[i]=line.length()>15?line.substring(0,15):line;}var event=new SignChangeEvent.Pre(sign,lines);NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return new Object[]{null,"not allowed"};SignText text=sign.getFrontText();for(int i=0;i<4;i++)text=text.setMessage(i,Component.literal(lines[i]));sign.setText(text,true);host.world().sendBlockUpdated(pos,sign.getBlockState(),sign.getBlockState(),3);NeoForge.EVENT_BUS.post(new SignChangeEvent.Post(sign,lines));return new Object[]{text(sign)};}
    @Override public void onMessage(Message message){if(message.name().equals("tablet.use")&&message.data().length>=4&&message.data()[0] instanceof CompoundTag result&&message.data()[3] instanceof BlockPos pos&&host.world().getBlockEntity(pos) instanceof SignBlockEntity sign)result.putString("signText",text(sign));}
}
