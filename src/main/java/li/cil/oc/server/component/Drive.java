package li.cil.oc.server.component;

import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.fs.Label;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.common.SaveHandler;
import net.minecraft.nbt.CompoundTag;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Original unmanaged HDD sectors, platter seek timing, locking and independent binary storage. */
public final class Drive extends AbstractManagedEnvironment implements DeviceInfo {
    private static final int SECTOR_SIZE=512;
    private static final double[] READ_SECTOR={1d/10,1d/20,1d/30,1d/40,1d/50,1d/60},WRITE_SECTOR={1d/5,1d/10,1d/15,1d/20,1d/25,1d/30},READ_BYTE={1d/48,1d/64,1d/80,1d/96,1d/112,1d/128},WRITE_BYTE={1d/24,1d/32,1d/40,1d/48,1d/56,1d/64};
    private final int capacity,platterCount,speed,sectorCount,sectorsPerPlatter;
    private final Label label;private final EnvironmentHost host;private final String sound;private final boolean locked;private final byte[] data;
    private int headPosition;
    public Drive(int capacity,int platterCount,Label label,EnvironmentHost host,String sound,int speed,boolean isLocked){if(capacity<SECTOR_SIZE||platterCount<1||platterCount>capacity/SECTOR_SIZE)throw new IllegalArgumentException("invalid drive geometry");this.capacity=capacity;this.platterCount=platterCount;this.label=label;this.host=host;this.sound=sound;this.speed=Math.max(0,Math.min(5,speed));locked=isLocked;data=new byte[capacity];sectorCount=capacity/SECTOR_SIZE;sectorsPerPlatter=sectorCount/platterCount;setNode(li.cil.oc.api.Network.newNode(this,Visibility.Network).withComponent("drive",Visibility.Neighbors).withConnector().create());}
    private Path savePath(){return li.cil.oc.server.fs.FileSystem.saveRoot().resolve(Settings.savePath).resolve(node().address()+".bin");}
    private static Object[] result(Object...values){return values;}
    public Map<String,String> getDeviceInfo(){return Map.of("class","disk","description","Hard disk drive","vendor","MightyPirates GmbH & Co. KG","product","MPD"+(capacity/1024)+"L"+platterCount,"capacity",Integer.toString((int)(capacity*1.024)),"size",Integer.toString(capacity),"clock",((int)(2000/READ_SECTOR[speed])/100)+"/"+((int)(2000/WRITE_SECTOR[speed])/100)+"/"+((int)(2000/READ_BYTE[speed])/100)+"/"+((int)(2000/WRITE_BYTE[speed])/100));}
    @Callback(direct=true,doc="function():string -- Get the drive label.") public synchronized Object[] getLabel(Context context,Arguments args){return label==null?null:result(label.getLabel());}
    @Callback(doc="function(value:string):string -- Set the drive label.") public synchronized Object[] setLabel(Context context,Arguments args)throws IOException{writable();if(label==null)throw new IOException("drive does not support labeling");label.setLabel(args.checkAny(0)==null?null:args.checkString(0));return result(label.getLabel());}
    @Callback(direct=true,doc="function():number -- Return capacity in bytes.") public Object[] getCapacity(Context context,Arguments args){return result(capacity);}
    @Callback(direct=true,doc="function():number -- Return sector size in bytes.") public Object[] getSectorSize(Context context,Arguments args){return result(SECTOR_SIZE);}
    @Callback(direct=true,doc="function():number -- Return platter count.") public Object[] getPlatterCount(Context context,Arguments args){return result(platterCount);}
    private int sector(Arguments args){int sector=args.checkInteger(0)-1;if(sector<0||sector>=sectorCount)throw new IllegalArgumentException("invalid offset, not in a usable sector");return sector;}
    private int offset(Arguments args){int offset=args.checkInteger(0)-1;if(offset<0||offset>=capacity)throw new IllegalArgumentException("invalid offset, not in a usable sector");return offset;}
    private void move(Context context,int sector){int target=sector%sectorsPerPlatter;if(target!=headPosition){if(Math.abs(target-headPosition)>Settings.get().sectorSeekThreshold)context.pause(Settings.get().sectorSeekTime);headPosition=target;}}
    private void writable()throws IOException{if(locked)throw new IOException("drive is read only");}
    @Callback(direct=true,doc="function(sector:number):string -- Read a complete sector.") public synchronized Object[] readSector(Context context,Arguments args){context.consumeCallBudget(READ_SECTOR[speed]);int sector=sector(args);move(context,sector);activity();return result((Object)Arrays.copyOfRange(data,sector*SECTOR_SIZE,(sector+1)*SECTOR_SIZE));}
    @Callback(direct=true,doc="function(sector:number,value:string) -- Overwrite bytes in a sector.") public synchronized Object[] writeSector(Context context,Arguments args)throws IOException{writable();context.consumeCallBudget(WRITE_SECTOR[speed]);byte[] value=args.checkByteArray(1);int sector=sector(args);move(context,sector);activity();System.arraycopy(value,0,data,sector*SECTOR_SIZE,Math.min(SECTOR_SIZE,value.length));return null;}
    @Callback(direct=true,doc="function(offset:number):number -- Read a byte at the one-based offset.") public synchronized Object[] readByte(Context context,Arguments args){context.consumeCallBudget(READ_BYTE[speed]);int offset=offset(args);move(context,offset/SECTOR_SIZE);activity();return result(data[offset]);}
    @Callback(direct=true,doc="function(offset:number,value:number) -- Write a byte at the one-based offset.") public synchronized Object[] writeByte(Context context,Arguments args)throws IOException{writable();context.consumeCallBudget(WRITE_BYTE[speed]);int offset=offset(args);move(context,offset/SECTOR_SIZE);activity();data[offset]=(byte)args.checkInteger(1);return null;}
    public synchronized void load(CompoundTag nbt){super.load(nbt);if(node().address()!=null){Path path=savePath();if(Files.isRegularFile(path))try(InputStream input=new GZIPInputStream(Files.newInputStream(path))){int offset=0;while(offset<data.length){int count=input.read(data,offset,data.length-offset);if(count<0)break;offset+=count;}}catch(IOException error){OpenComputers.LOGGER.warn("Failed loading drive {}",node().address(),error);}}headPosition=Math.max(0,Math.min(nbt.getInt("headPos"),sectorsPerPlatter-1));if(label!=null)label.load(nbt);}
    public synchronized void save(CompoundTag nbt){super.save(nbt);nbt.putInt("headPos",headPosition);if(label!=null)label.save(nbt);if(SaveHandler.isSavingForClients()||node().address()==null)return;Path path=savePath();try{Files.createDirectories(path.getParent());try(OutputStream output=new GZIPOutputStream(Files.newOutputStream(path))){output.write(data);}}catch(IOException error){OpenComputers.LOGGER.warn("Failed saving drive {}",node().address(),error);}}
    private void activity(){if(host!=null&&sound!=null)li.cil.oc.server.PacketSender.sendFileSystemActivity(node(),host,sound);}
}
