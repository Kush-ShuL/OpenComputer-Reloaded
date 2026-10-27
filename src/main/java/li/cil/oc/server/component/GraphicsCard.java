package li.cil.oc.server.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.internal.TextBuffer;
import li.cil.oc.api.internal.TextBuffer.ColorDepth;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.LimitReachedException;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.common.component.GpuTextBuffer;
import li.cil.oc.common.component.traits.VideoRamDevice;
import li.cil.oc.common.component.traits.VideoRamRasterizer;
import li.cil.oc.util.ExtendedUnicodeHelper;
import li.cil.oc.util.PackedColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** Original GPU color, text, viewport and persistent off-screen VRAM operations. */
public class GraphicsCard extends AbstractManagedEnvironment implements DeviceInfo, VideoRamDevice {
    public final int tier;
    private final int maxWidth, maxHeight;
    private final ColorDepth maxDepth;
    private final double totalVram, bitbltCost;
    private final Map<Integer, GpuTextBuffer> pages = new LinkedHashMap<>();
    private String screenAddress;
    private TextBuffer screen;
    private int active;
    private boolean budgetExhausted;
    private static final double[] BACKGROUND = {1d/32, 1d/64, 1d/128}, FOREGROUND = BACKGROUND,
            PALETTE = {1d/2, 1d/8, 1d/16}, SET = {1d/64, 1d/128, 1d/256},
            COPY = {1d/16, 1d/32, 1d/64}, FILL = {1d/32, 1d/64, 1d/128};

    public GraphicsCard(int tier) {
        if (tier < 0 || tier > 2) throw new IllegalArgumentException("invalid graphics card tier");
        this.tier=tier; maxWidth=Settings.screenResolutionsByTier[tier][0]; maxHeight=Settings.screenResolutionsByTier[tier][1];
        maxDepth=Settings.screenDepthsByTier[tier]; totalVram=(double)maxWidth*maxHeight*Settings.get().vramSizes[tier];
        bitbltCost=Settings.get().bitbltCost*Math.pow(2,tier);
        setNode(Network.newNode(this, Visibility.Neighbors).withComponent("gpu").withConnector().create());
    }
    @Override public Map<Integer,GpuTextBuffer> videoBuffers() { return pages; }
    private TextBuffer buffer(int index) {
        if (index != 0) return pages.get(index);
        if (screen == null && screenAddress != null && node().network() != null) {
            Node found = node().network().node(screenAddress);
            if (found != null && found.host() instanceof TextBuffer text) screen = text;
        }
        return screen;
    }
    private Object[] screen(int index, Function<TextBuffer,Object[]> action) {
        TextBuffer target = buffer(index);
        if (target == null) return new Object[]{null, index == 0 ? "no screen" : "invalid buffer index"};
        synchronized(target) { return action.apply(target); }
    }
    private Object[] screen(Function<TextBuffer,Object[]> action) { return screen(active,action); }
    private double energy(String name) { return Math.max(0,Settings.get().config.getDouble("power.cost.gpu"+name))/(Settings.screenResolutionsByTier[0][0]*Settings.screenResolutionsByTier[0][1]); }
    private boolean costs(int index, Context context, double budget, double units, double cost) {
        if (index != 0) return true;
        context.consumeCallBudget(budget);
        return ((ComponentConnector)node()).tryChangeBuffer(-units*cost);
    }
    private void checkResolution(int width,int height,String error) {
        if (width<1||height<1||width>maxWidth||height>maxWidth||(long)width*height>(long)maxWidth*maxHeight)
            throw new IllegalArgumentException(error);
    }
    @Override public Map<String,String> getDeviceInfo() {
        String clock=(int)(2000/BACKGROUND[tier])/100+"/"+(int)(2000/FOREGROUND[tier])/100+"/"+(int)(2000/PALETTE[tier])/100+"/"+(int)(2000/SET[tier])/100+"/"+(int)(2000/COPY[tier])/100+"/"+(int)(2000/FILL[tier])/100;
        return Map.of("class","display","description","Graphics controller","vendor","MightyPirates GmbH & Co. KG",
                "product","MPG"+((tier+1)*1000)+" GTZ","capacity",Integer.toString(maxWidth*maxHeight),
                "width",Integer.toString(PackedColor.Depth.bits(maxDepth)),"clock",clock);
    }

    @Callback(direct=true,doc="function():number -- Get the active buffer; zero is the bound screen.")
    public synchronized Object[] getActiveBuffer(Context context,Arguments args) { return new Object[]{active}; }
    @Callback(direct=true,doc="function(index:number):number -- Select a VRAM page or the screen and return the previous index.")
    public synchronized Object[] setActiveBuffer(Context context,Arguments args) {
        int index=args.checkInteger(0); if(index!=0&&!pages.containsKey(index))return new Object[]{null,"invalid buffer index"};
        int previous=active;active=index;return new Object[]{previous};
    }
    @Callback(direct=true,doc="function():table -- Get allocated VRAM page indices.")
    public synchronized Object[] buffers(Context context,Arguments args) { return new Object[]{bufferIndexes()}; }
    @Callback(direct=true,doc="function([width:number,height:number]):number -- Allocate an off-screen page.")
    public synchronized Object[] allocateBuffer(Context context,Arguments args) {
        int width=args.optInteger(0,maxWidth),height=args.optInteger(1,maxHeight);
        if(width<=0||height<=0)return new Object[]{null,"invalid page dimensions: must be greater than zero"};
        if((long)width*height>totalVram-calculateUsedMemory())return new Object[]{null,"not enough video memory"};
        if(node()==null)return new Object[]{null,"graphics card appears disconnected"};
        int index=nextAvailableBufferIndex();
        addBuffer(new GpuTextBuffer(node().address(),index,new li.cil.oc.util.TextBuffer(width,height,PackedColor.Depth.format(maxDepth))));
        return new Object[]{index};
    }
    @Override public void onBufferRamDestroy(int id) {
        if(id!=RESERVED_SCREEN_INDEX){
            TextBuffer target=buffer(0);
            if(target instanceof VideoRamRasterizer rasterizer)rasterizer.removeBuffer(node().address(),id);
        }
        if(id==active)active=0;
    }
    @Callback(direct=true,doc="function([index:number]):boolean -- Free a VRAM page.")
    public synchronized Object[] freeBuffer(Context context,Arguments args) {
        return removeBuffers(new int[]{args.optInteger(0,active)})==1?new Object[]{true}:new Object[]{null,"no buffer at index"};
    }
    @Callback(direct=true,doc="function():number -- Free all off-screen pages.")
    public synchronized Object[] freeAllBuffers(Context context,Arguments args) { return new Object[]{removeAllBuffers()}; }
    @Callback(direct=true,doc="function():number -- Total video memory in character cells.")
    public Object[] totalMemory(Context context,Arguments args) { return new Object[]{totalVram}; }
    @Callback(direct=true,doc="function():number -- Free video memory in character cells.")
    public synchronized Object[] freeMemory(Context context,Arguments args) { return new Object[]{totalVram-calculateUsedMemory()}; }
    @Callback(direct=true,doc="function([index:number]):number,number -- Get page dimensions.")
    public synchronized Object[] getBufferSize(Context context,Arguments args) { return screen(args.optInteger(0,active),s->new Object[]{s.getWidth(),s.getHeight()}); }

    @Callback(direct=true,doc="function([dst,col,row,width,height,src,fromCol,fromRow]):boolean -- Copy a rectangle between pages or the screen.")
    public synchronized Object[] bitblt(Context context,Arguments args)throws LimitReachedException {
        int destinationIndex=args.optInteger(0,0);
        TextBuffer destination=buffer(destinationIndex);
        if(destination==null)return new Object[]{null,destinationIndex==0?"no screen":"invalid buffer index"};
        int col=args.optInteger(1,1),row=args.optInteger(2,1),width=args.optInteger(3,destination.getWidth()),height=args.optInteger(4,destination.getHeight());
        int sourceIndex=args.optInteger(5,active);
        TextBuffer source=buffer(sourceIndex);
        if(source==null)return new Object[]{null,sourceIndex==0?"no screen":"invalid buffer index"};
        int fromCol=args.optInteger(6,1),fromRow=args.optInteger(7,1);
        double budget=source instanceof GpuTextBuffer page && !(destination instanceof GpuTextBuffer)
                ? page.dirty ? bitbltCost*(double)source.getWidth()*source.getHeight()/(maxWidth*maxHeight):.001:0;
        double credit=(tier+1)*.5,over=budget-credit;
        if(over>0){if(budgetExhausted){if(over>credit)context.pause((over-credit)/credit/20);budget=0;}else{budgetExhausted=true;throw new LimitReachedException();}}
        budgetExhausted=false;
        double energy=destination instanceof GpuTextBuffer?0:energy("Copy")/15;
        if(!costs(destinationIndex,context,budget,Math.max(0L,(long)width*height),energy))return new Object[]{null,"not enough energy"};
        synchronized(destination){synchronized(source){
            if(destinationIndex==sourceIndex)destination.copy(fromCol-1,fromRow-1,width,height,col-fromCol,row-fromRow);
            else GpuTextBuffer.bitblt(destination,col,row,width,height,source,fromCol,fromRow);
        }}
        return new Object[]{true};
    }

    @Callback(doc="function(address:string[,reset:boolean=true]):boolean -- Bind a screen in the component network.")
    public synchronized Object[] bind(Context context,Arguments args) {
        String address=args.checkString(0);boolean reset=args.optBoolean(1,true);
        if(node().network()==null)return new Object[]{null,"invalid address"};
        Node target=node().network().node(address);
        if(target==null)return new Object[]{null,"invalid address"};
        if(!(target.host() instanceof TextBuffer text))return new Object[]{null,"not a screen"};
        screenAddress=target.address();screen=text;
        // The original callback resets the currently selected target, including a selected VRAM page.
        return screen(selected->{
            if(reset){
                selected.setResolution(Math.min(maxWidth,selected.getMaximumWidth()),Math.min(maxHeight,selected.getMaximumHeight()));
                selected.setColorDepth(ColorDepth.values()[Math.min(maxDepth.ordinal(),selected.getMaximumColorDepth().ordinal())]);
                selected.setForegroundColor(0xffffff);selected.setBackgroundColor(0);
                if(selected instanceof VideoRamRasterizer rasterizer)rasterizer.removeAllBuffers();
            }else context.pause(0);
            return new Object[]{true};
        });
    }
    @Callback(direct=true,doc="function():string -- Get the bound screen address.")
    public synchronized Object[] getScreen(Context context,Arguments args) { return screen(0,s->new Object[]{s.node().address()}); }
    @Callback(direct=true,doc="function():number,boolean -- Get background color and palette flag.")
    public synchronized Object[] getBackground(Context context,Arguments args) { return screen(s->new Object[]{s.getBackgroundColor(),s.isBackgroundFromPalette()}); }
    @Callback(direct=true,doc="function(color:number[,palette:boolean]):number,number -- Set background color.")
    public synchronized Object[] setBackground(Context context,Arguments args) {
        int color=args.checkInteger(0);
        if(active==0)context.consumeCallBudget(BACKGROUND[tier]);
        return screen(s->{int previous=s.getBackgroundColor();boolean palette=s.isBackgroundFromPalette();int rgb=palette?s.getPaletteColor(previous):previous;
            s.setBackgroundColor(color,args.optBoolean(1,false));return new Object[]{rgb,palette?previous:null};});
    }
    @Callback(direct=true,doc="function():number,boolean -- Get foreground color and palette flag.")
    public synchronized Object[] getForeground(Context context,Arguments args) { return screen(s->new Object[]{s.getForegroundColor(),s.isForegroundFromPalette()}); }
    @Callback(direct=true,doc="function(color:number[,palette:boolean]):number,number -- Set foreground color.")
    public synchronized Object[] setForeground(Context context,Arguments args) {
        int color=args.checkInteger(0);
        if(active==0)context.consumeCallBudget(FOREGROUND[tier]);
        return screen(s->{int previous=s.getForegroundColor();boolean palette=s.isForegroundFromPalette();int rgb=palette?s.getPaletteColor(previous):previous;
            s.setForegroundColor(color,args.optBoolean(1,false));return new Object[]{rgb,palette?previous:null};});
    }
    @Callback(direct=true,doc="function(index:number):number -- Get a palette entry.")
    public synchronized Object[] getPaletteColor(Context context,Arguments args) {
        int index=args.checkInteger(0);
        return screen(s->{try{return new Object[]{s.getPaletteColor(index)};}catch(IndexOutOfBoundsException error){throw new IllegalArgumentException("invalid palette index");}});
    }
    @Callback(direct=true,doc="function(index:number,color:number):number -- Change a palette entry and return its old RGB value.")
    public synchronized Object[] setPaletteColor(Context context,Arguments args) {
        int index=args.checkInteger(0),color=args.checkInteger(1);
        if(active==0){context.consumeCallBudget(PALETTE[tier]);context.pause(.1);}
        return screen(s->{try{int old=s.getPaletteColor(index);s.setPaletteColor(index,color);return new Object[]{old};}catch(IndexOutOfBoundsException error){throw new IllegalArgumentException("invalid palette index");}});
    }
    @Callback(direct=true,doc="function():number -- Get the current color depth.")
    public synchronized Object[] getDepth(Context context,Arguments args) { return screen(s->new Object[]{PackedColor.Depth.bits(s.getColorDepth())}); }
    @Callback(doc="function(depth:number):number -- Change color depth and return the previous depth.")
    public synchronized Object[] setDepth(Context context,Arguments args) {
        int bits=args.checkInteger(0);
        return screen(s->{
            ColorDepth depth=switch(bits){case 1->ColorDepth.OneBit;case 4->ColorDepth.FourBit;case 8->ColorDepth.EightBit;default->throw new IllegalArgumentException("unsupported depth");};
            if(depth.ordinal()>maxDepth.ordinal())throw new IllegalArgumentException("unsupported depth");
            ColorDepth previous=s.getColorDepth();s.setColorDepth(depth);return new Object[]{previous};
        });
    }
    @Callback(direct=true,doc="function():number -- Get the shared maximum color depth of this GPU and its screen.")
    public synchronized Object[] maxDepth(Context context,Arguments args) { return screen(s->new Object[]{PackedColor.Depth.bits(ColorDepth.values()[Math.min(maxDepth.ordinal(),s.getMaximumColorDepth().ordinal())])}); }
    @Callback(direct=true,doc="function():number,number -- Get current resolution.")
    public synchronized Object[] getResolution(Context context,Arguments args) { return screen(s->new Object[]{s.getWidth(),s.getHeight()}); }
    @Callback(doc="function(width:number,height:number):boolean -- Set the active buffer resolution.")
    public synchronized Object[] setResolution(Context context,Arguments args) {
        int width=args.checkInteger(0),height=args.checkInteger(1);checkResolution(width,height,"unsupported resolution");
        return screen(s->new Object[]{s.setResolution(width,height)});
    }
    @Callback(direct=true,doc="function():number,number -- Get maximum shared resolution.")
    public synchronized Object[] maxResolution(Context context,Arguments args) { return screen(s->new Object[]{Math.min(maxWidth,s.getMaximumWidth()),Math.min(maxHeight,s.getMaximumHeight())}); }
    @Callback(direct=true,doc="function():number,number -- Get the viewport dimensions.")
    public synchronized Object[] getViewport(Context context,Arguments args) { return screen(s->new Object[]{s.getViewportWidth(),s.getViewportHeight()}); }
    @Callback(doc="function(width:number,height:number):boolean -- Set a viewport within the active buffer.")
    public synchronized Object[] setViewport(Context context,Arguments args) {
        int width=args.checkInteger(0),height=args.checkInteger(1);checkResolution(width,height,"unsupported viewport size");
        return screen(s->{if(width>s.getWidth()||height>s.getHeight())throw new IllegalArgumentException("unsupported viewport size");return new Object[]{s.setViewport(width,height)};});
    }
    @Callback(direct=true,doc="function(x:number,y:number):string,number,number,number,number -- Read a character and its colors/palette indices.")
    public synchronized Object[] get(Context context,Arguments args) {
        int x=args.checkInteger(0)-1,y=args.checkInteger(1)-1;
        return screen(s->{int foreground=s.getForegroundColor(x,y),background=s.getBackgroundColor(x,y);boolean fp=s.isForegroundFromPalette(x,y),bp=s.isBackgroundFromPalette(x,y);
            return new Object[]{new String(Character.toChars(s.getCodePoint(x,y))),fp?s.getPaletteColor(foreground):foreground,bp?s.getPaletteColor(background):background,fp?foreground:null,bp?background:null};});
    }
    @Callback(direct=true,doc="function(x:number,y:number,text:string[,vertical:boolean]):boolean -- Write Unicode text.")
    public synchronized Object[] set(Context context,Arguments args) {
        int x=args.checkInteger(0)-1,y=args.checkInteger(1)-1;String text=args.checkString(2);boolean vertical=args.optBoolean(3,false);
        return screen(s->{if(!costs(active,context,SET[tier],ExtendedUnicodeHelper.length(text),energy("Set")))return new Object[]{null,"not enough energy"};s.set(x,y,text,vertical);return new Object[]{true};});
    }
    @Callback(direct=true,doc="function(x,y,width,height,tx,ty):boolean -- Translate a rectangle within the active buffer.")
    public synchronized Object[] copy(Context context,Arguments args) {
        int x=args.checkInteger(0)-1,y=args.checkInteger(1)-1,width=Math.max(0,args.checkInteger(2)),height=Math.max(0,args.checkInteger(3)),tx=args.checkInteger(4),ty=args.checkInteger(5);
        return screen(s->{if(!costs(active,context,COPY[tier],(double)width*height,energy("Copy")))return new Object[]{null,"not enough energy"};s.copy(x,y,width,height,tx,ty);return new Object[]{true};});
    }
    @Callback(direct=true,doc="function(x,y,width,height,char:string):boolean -- Fill a rectangle with one Unicode codepoint.")
    public synchronized Object[] fill(Context context,Arguments args) {
        int x=args.checkInteger(0)-1,y=args.checkInteger(1)-1,width=Math.max(0,args.checkInteger(2)),height=Math.max(0,args.checkInteger(3));String text=args.checkString(4);
        if(ExtendedUnicodeHelper.length(text)!=1)throw new IllegalArgumentException("invalid fill value");
        int cp=text.codePointAt(0);
        return screen(s->{if(!costs(active,context,FILL[tier],(double)width*height,energy(cp==' '?"Clear":"Fill")))return new Object[]{null,"not enough energy"};s.fill(x,y,width,height,cp);return new Object[]{true};});
    }

    @Override public synchronized void onMessage(Message message) {
        if(!node().isNeighborOf(message.source()))return;
        if(message.name().equals("computer.started")||message.name().equals("computer.stopped")){active=0;removeAllBuffers();}
        if(message.name().equals("computer.stopped"))screen(s->{
            s.setResolution(Math.min(maxWidth,s.getMaximumWidth()),Math.min(maxHeight,s.getMaximumHeight()));
            s.setColorDepth(ColorDepth.values()[Math.min(maxDepth.ordinal(),s.getMaximumColorDepth().ordinal())]);s.setForegroundColor(0xffffff);
            String error=message.source().host() instanceof li.cil.oc.api.machine.Machine machine?machine.lastError():null;
            s.setBackgroundColor(error!=null&&s.getColorDepth()!=ColorDepth.OneBit?0x0000ff:0);s.fill(0,0,s.getWidth(),s.getHeight(),' ');
            if(error!=null)drawError(s,error);
            return null;
        });
    }
    private static void drawError(TextBuffer screen,String error) {
        int width=screen.getWidth(),height=screen.getHeight();
        String key=Language.getInstance().has("oc:"+error)?"oc:"+error:error;
        String localized=Language.getInstance().has(key)?Component.translatable(key).getString():error;
        localized=localized.replace("[nl]","\n").lines().map(String::strip).collect(java.util.stream.Collectors.joining("\n"));
        String wrapped=Pattern.compile("(.{1,"+Math.max(1,width-2)+"})\\s").matcher(localized.replace("\t","  ")+"\n")
                .replaceAll(match->Matcher.quoteReplacement(match.group(1)+"\n"));
        List<String> lines=wrapped.lines().toList();
        int first=Math.max(2,(height-lines.size())/2);String title="Unrecoverable Error";screen.set((width-title.length())/2,first-2,title,false);
        int length=lines.stream().mapToInt(String::length).max().orElse(0),column=Math.max(0,(width-length)/2);
        for(int i=0;i<lines.size()&&first+i<height;i++)screen.set(column,first+i,lines.get(i),false);
    }
    @Override public synchronized void onConnect(Node node) { if(screen==null&&screenAddress!=null&&screenAddress.equals(node.address())&&node.host() instanceof TextBuffer text)screen=text; }
    @Override public synchronized void onDisconnect(Node node) { if(node==node()||screenAddress!=null&&screenAddress.equals(node.address())){screenAddress=null;screen=null;} }
    @Override public synchronized void load(CompoundTag tag) {
        super.load(tag);
        removeAllBuffers();
        if(tag.contains("screen",Tag.TAG_STRING)){screen=null;screenAddress=tag.getString("screen");if(screenAddress.isEmpty())screenAddress=null;}
        for(Tag raw:tag.getCompound("videoRam").getList("pages",Tag.TAG_COMPOUND)){
            CompoundTag page=(CompoundTag)raw;int index=page.getInt("page_idx");if(index>0)loadBuffer(node().address(),index,page.getCompound("page_data"));
        }
        active=tag.getInt("bufferIndex");if(active!=0&&!pages.containsKey(active))active=0;
    }
    @Override public synchronized void save(CompoundTag tag) {
        super.save(tag);if(screenAddress==null)tag.remove("screen");else tag.putString("screen",screenAddress);tag.putInt("bufferIndex",active);
        CompoundTag video=new CompoundTag();ListTag data=new ListTag();
        pages.forEach((index,page)->{CompoundTag entry=new CompoundTag(),contents=new CompoundTag();entry.putInt("page_idx",index);page.data().save(contents);entry.put("page_data",contents);data.add(entry);});
        video.put("pages",data);tag.put("videoRam",video);
    }
}
