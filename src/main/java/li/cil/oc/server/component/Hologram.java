package li.cil.oc.server.component;

import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;

/** Source-derived 48x32x48 two-bit projector memory and complete Lua interface. */
public final class Hologram extends AbstractManagedEnvironment implements DeviceInfo {
    public interface Host extends EnvironmentHost { void synchronizeHologram(CompoundTag update); }
    public static final int WIDTH=48, HEIGHT=32, PLANE=WIDTH*WIDTH;
    public final int tier;
    public final Host host;
    public final int[] volume=new int[PLANE*2];
    public final int[] colors;
    public double scale=1,litRatio=-1;
    public Vec3 translation=Vec3.ZERO;
    public boolean hasPower=true;
    public float rotationAngle,rotationX,rotationY,rotationZ,rotationSpeed,rotationSpeedX,rotationSpeedY,rotationSpeedZ;
    public long renderRevision;
    private final BitSet dirty=new BitSet(PLANE);
    private boolean settingsDirty;
    public Hologram(Host host,int tier){this.host=host;this.tier=Math.clamp(tier,0,1);colors=this.tier==0?new int[]{0x00FF00}:new int[]{0xFF0000,0x00FF00,0x0000FF};setNode(Network.newNode(this,Visibility.Network).withComponent("hologram").withConnector().create());}
    @Override public ComponentConnector node(){return (ComponentConnector)super.node();}
    @Override public Map<String,String> getDeviceInfo(){return Map.of(DeviceAttribute.Class,DeviceClass.Display,DeviceAttribute.Description,"Holographic projector",DeviceAttribute.Vendor,Constants.DeviceInfo.DefaultVendor,DeviceAttribute.Product,"VirtualViewer H1-"+(tier+1),DeviceAttribute.Capacity,Integer.toString(WIDTH*WIDTH*HEIGHT),DeviceAttribute.Width,Integer.toString(colors.length));}
    public int getColor(int x,int y,int z){int column=x+z*WIDTH;return ((volume[column]>>>y)&1)|(((volume[column+PLANE]>>>y)&1)<<1);}
    public synchronized void setColor(int x,int y,int z,int value){if(getColor(x,y,z)==(value&3))return;int index=x+z*WIDTH,mask=1<<y;volume[index]=(volume[index]&~mask)|((value&1)<<y);volume[index+PLANE]=(volume[index+PLANE]&~mask)|(((value>>>1)&1)<<y);dirty(index);}
    private void dirty(int index){dirty.set(index);litRatio=-1;renderRevision++;host.markChanged();}
    private void settingsChanged(){settingsDirty=true;renderRevision++;host.markChanged();}
    private static int coordinate(Arguments args,int index,int maximum,String axis){int v=args.checkInteger(index)-1;if(v<0||v>=maximum)throw new IndexOutOfBoundsException(axis);return v;}
    private int value(Arguments args,int index){int value=args.isBoolean(index)?args.checkBoolean(index)?1:0:args.checkInteger(index);if(value<0||value>colors.length)throw new IllegalArgumentException("invalid value");return value;}
    private static double finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("value must be finite");return value;}
    @Callback(doc="function() -- Clears the hologram.") public synchronized Object[] clear(Context context,Arguments args){Arrays.fill(volume,0);dirty.set(0,PLANE);litRatio=0;settingsChanged();return null;}
    @Callback(direct=true,doc="function(x:number,y:number,z:number):number -- Returns a voxel color.") public synchronized Object[] get(Context c,Arguments a){return new Object[]{getColor(coordinate(a,0,WIDTH,"x"),coordinate(a,1,HEIGHT,"y"),coordinate(a,2,WIDTH,"z"))};}
    @Callback(direct=true,limit=256,doc="function(x:number,y:number,z:number,value:number or boolean) -- Sets a voxel.") public synchronized Object[] set(Context c,Arguments a){setColor(coordinate(a,0,WIDTH,"x"),coordinate(a,1,HEIGHT,"y"),coordinate(a,2,WIDTH,"z"),value(a,3));return null;}
    @Callback(direct=true,limit=128,doc="function(x:number,z:number[,minY:number],maxY:number,value:number or boolean) -- Fills a vertical interval.") public synchronized Object[] fill(Context c,Arguments a){int x=coordinate(a,0,WIDTH,"x"),z=coordinate(a,1,WIDTH,"z"),min,max,value;if(a.count()>4){min=Math.clamp(a.checkInteger(2),1,32);max=Math.clamp(a.checkInteger(3),1,32);value=value(a,4);}else{min=1;max=Math.clamp(a.checkInteger(2),1,32);value=value(a,3);}if(min>max)throw new IllegalArgumentException("interval is empty");int mask=(-1>>>(31-(max-min)))<<(min-1),index=x+z*WIDTH;volume[index]=(value&1)==0?volume[index]&~mask:volume[index]|mask;volume[index+PLANE]=(value&2)==0?volume[index+PLANE]&~mask:volume[index+PLANE]|mask;dirty(index);return null;}
    @Callback(doc="function(data:string) -- Sets raw voxel bytes in x,z,y order.") public synchronized Object[] setRaw(Context c,Arguments a){byte[] bytes=a.checkByteArray(0);for(int x=0;x<WIDTH;x++)for(int z=0;z<WIDTH;z++){int offset=(x*WIDTH+z)*HEIGHT;if(bytes.length<offset+HEIGHT)continue;int low=0,high=0;for(int y=0;y<HEIGHT;y++){low|=(bytes[offset+y]&1)<<y;high|=((bytes[offset+y]>>>1)&1)<<y;}int index=x+z*WIDTH;if(volume[index]!=low||volume[index+PLANE]!=high){volume[index]=low;volume[index+PLANE]=high;dirty(index);}}c.pause(Settings.get().hologramSetRawDelay);return null;}
    @Callback(doc="function(x:number,z:number,sx:number,sz:number,tx:number,tz:number) -- Copies translated columns.") public synchronized Object[] copy(Context c,Arguments a){int x=coordinate(a,0,WIDTH,"x"),z=coordinate(a,1,WIDTH,"z"),w=a.checkInteger(2),h=a.checkInteger(3),tx=a.checkInteger(4),tz=a.checkInteger(5);if(w<=0||h<=0||tx==0&&tz==0)return null;int[] before=volume.clone();int left=Math.clamp((long)x+tx,0,WIDTH),right=Math.clamp((long)x+tx+w,0,WIDTH),top=Math.clamp((long)z+tz,0,WIDTH),bottom=Math.clamp((long)z+tz+h,0,WIDTH);for(int nz=top;nz<bottom;nz++)for(int nx=left;nx<right;nx++){int ox=nx-tx,oz=nz-tz;if(ox<0||ox>=WIDTH||oz<0||oz>=WIDTH)continue;int to=nx+nz*WIDTH,from=ox+oz*WIDTH;volume[to]=before[from];volume[to+PLANE]=before[from+PLANE];dirty(to);}c.pause(Math.max(0,(right-left)*(bottom-top)/(double)PLANE-0.25));return null;}
    @Callback(direct=true,doc="function():number -- Returns the projection scale.") public Object[] getScale(Context c,Arguments a){return new Object[]{scale};}
    @Callback(doc="function(value:number) -- Sets projection scale and energy cost.") public Object[] setScale(Context c,Arguments a){scale=Math.max(0.333333,Math.min(Settings.get().hologramMaxScaleByTier[tier],finite(a.checkDouble(0))));settingsChanged();return null;}
    @Callback(direct=true,doc="function():number,number,number -- Returns projection offsets.") public Object[] getTranslation(Context c,Arguments a){return new Object[]{translation.x,translation.y,translation.z};}
    @Callback(doc="function(tx:number,ty:number,tz:number) -- Sets projection offsets.") public Object[] setTranslation(Context c,Arguments a){double max=Settings.get().hologramMaxTranslationByTier[tier];translation=new Vec3(Math.clamp(finite(a.checkDouble(0)),-max,max),Math.clamp(finite(a.checkDouble(1)),0,max*2),Math.clamp(finite(a.checkDouble(2)),-max,max));settingsChanged();return null;}
    @Callback(direct=true,doc="function():number -- Returns supported color depth.") public Object[] maxDepth(Context c,Arguments a){return new Object[]{tier+1};}
    private int paletteIndex(Arguments a){int index=a.checkInteger(0)-1;if(index<0||index>=colors.length)throw new IndexOutOfBoundsException("palette index");return index;}
    @Callback(doc="function(index:number):number -- Returns an RGB palette entry.") public Object[] getPaletteColor(Context c,Arguments a){return new Object[]{colors[paletteIndex(a)]};}
    @Callback(doc="function(index:number,value:number):number -- Changes a palette entry and returns its previous RGB value.") public Object[] setPaletteColor(Context c,Arguments a){int index=paletteIndex(a),old=colors[index];colors[index]=a.checkInteger(1)&0xFFFFFF;settingsChanged();return new Object[]{old};}
    @Callback(doc="function(angle:number,x:number,y:number,z:number):boolean -- Sets base rotation on tier two.") public Object[] setRotation(Context c,Arguments a){if(tier<1)return new Object[]{null,"not supported"};rotationAngle=(float)(finite(a.checkDouble(0))%360);rotationX=(float)finite(a.checkDouble(1));rotationY=(float)finite(a.checkDouble(2));rotationZ=(float)finite(a.checkDouble(3));settingsChanged();return new Object[]{true};}
    @Callback(doc="function(speed:number,x:number,y:number,z:number):boolean -- Sets rotation speed on tier two.") public Object[] setRotationSpeed(Context c,Arguments a){if(tier<1)return new Object[]{null,"not supported"};rotationSpeed=(float)Math.clamp(finite(a.checkDouble(0)),-1440,1440);rotationSpeedX=(float)finite(a.checkDouble(1));rotationSpeedY=(float)finite(a.checkDouble(2));rotationSpeedZ=(float)finite(a.checkDouble(3));settingsChanged();return new Object[]{true};}
    @Callback(direct=true,doc="function():number,number,number -- Returns voxel dimensions.") public Object[] getDimensions(Context c,Arguments a){return new Object[]{WIDTH,HEIGHT,WIDTH};}
    @Override public boolean canUpdate(){return true;}
    @Override public synchronized void update(){if(host.world()==null||host.world().isClientSide)return;int frequency=Math.max(1,(int)Settings.get().tickFrequency);if(host.world().getGameTime()%frequency==0){if(litRatio<0){litRatio=Arrays.stream(volume).filter(v->v!=0).count()/(double)volume.length;}boolean previous=hasPower;hasPower=Settings.get().ignorePower||node().tryChangeBuffer(-Settings.get().hologramCost*litRatio*scale*frequency);if(previous!=hasPower)settingsDirty=true;}if(!dirty.isEmpty()||settingsDirty){CompoundTag update=new CompoundTag();writeSettings(update);if(dirty.cardinality()>PLANE*0.8)update.putIntArray("volume",volume.clone());else{int[] values=new int[dirty.cardinality()*3];int n=0;for(int index=dirty.nextSetBit(0);index>=0;index=dirty.nextSetBit(index+1)){values[n++]=index;values[n++]=volume[index];values[n++]=volume[index+PLANE];}update.putIntArray("columns",values);}host.synchronizeHologram(update);dirty.clear();settingsDirty=false;}}
    private void writeSettings(CompoundTag tag){tag.putIntArray("colors",colors.clone());tag.putDouble("oc:scale",scale);tag.putBoolean("oc:hasPower",hasPower);tag.putDouble("oc:offsetX",translation.x);tag.putDouble("oc:offsetY",translation.y);tag.putDouble("oc:offsetZ",translation.z);tag.putFloat("oc:rotationAngle",rotationAngle);tag.putFloat("oc:rotationX",rotationX);tag.putFloat("oc:rotationY",rotationY);tag.putFloat("oc:rotationZ",rotationZ);tag.putFloat("oc:rotationSpeed",rotationSpeed);tag.putFloat("oc:rotationSpeedX",rotationSpeedX);tag.putFloat("oc:rotationSpeedY",rotationSpeedY);tag.putFloat("oc:rotationSpeedZ",rotationSpeedZ);}
    public synchronized void applyUpdate(CompoundTag tag){if(tag.contains("volume")){int[] data=tag.getIntArray("volume");System.arraycopy(data,0,volume,0,Math.min(data.length,volume.length));}if(tag.contains("columns")){int[] data=tag.getIntArray("columns");for(int n=0;n+2<data.length;n+=3){int index=data[n];if(index>=0&&index<PLANE){volume[index]=data[n+1];volume[index+PLANE]=data[n+2];}}}int[] palette=tag.getIntArray("colors");System.arraycopy(palette,0,colors,0,Math.min(palette.length,colors.length));scale=tag.contains("oc:scale")?Math.max(0.333333,tag.getDouble("oc:scale")):1;hasPower=!tag.contains("oc:hasPower")||tag.getBoolean("oc:hasPower");translation=new Vec3(tag.getDouble("oc:offsetX"),tag.getDouble("oc:offsetY"),tag.getDouble("oc:offsetZ"));rotationAngle=tag.getFloat("oc:rotationAngle");rotationX=tag.getFloat("oc:rotationX");rotationY=tag.getFloat("oc:rotationY");rotationZ=tag.getFloat("oc:rotationZ");rotationSpeed=tag.getFloat("oc:rotationSpeed");rotationSpeedX=tag.getFloat("oc:rotationSpeedX");rotationSpeedY=tag.getFloat("oc:rotationSpeedY");rotationSpeedZ=tag.getFloat("oc:rotationSpeedZ");litRatio=-1;renderRevision++;}
    @Override public synchronized void load(CompoundTag tag){super.load(tag);applyUpdate(tag);}
    @Override public synchronized void save(CompoundTag tag){super.save(tag);tag.putByte("oc:tier",(byte)tier);tag.putIntArray("volume",volume.clone());writeSettings(tag);}
}
