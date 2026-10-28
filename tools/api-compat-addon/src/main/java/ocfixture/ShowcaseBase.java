package ocfixture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import li.cil.oc.api.CreativeTab;
import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.item.MutableProcessor;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Drone;
import li.cil.oc.api.internal.Rack;
import li.cil.oc.api.internal.Robot;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Connector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** A persistent playable laboratory. All automation runs on the actual OC Lua machines. */
public final class ShowcaseBase {
    public record Report(BlockPos spawn, Map<String, BlockPos> stations, List<String> runningMachines) {}
    private ShowcaseBase() {}

    public static CompletableFuture<Report> build(ServerLevel level, ServerPlayer player, Path worldDirectory) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Build the showcase on the actual server thread");
        Path actual = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!actual.equals(worldDirectory.toAbsolutePath().normalize())) throw new IllegalArgumentException("Wrong persistent world directory");
        Build build = new Build(level, player, actual);
        try {
            build.construct();
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> build.tick(event));
        } catch (Throwable error) { build.result.completeExceptionally(error); }
        return build.result;
    }

    /** Recommission only an unfinished save created by this builder; keep its structures and inventory. */
    public static CompletableFuture<Report> resume(ServerLevel level, ServerPlayer player, Path worldDirectory) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Resume the showcase on the actual server thread");
        Path actual=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if(!actual.equals(worldDirectory.toAbsolutePath().normalize())||!Files.isDirectory(actual.resolve("opencomputers/showcase/programs"))||Files.exists(actual.resolve("opencomputers/showcase/READY.txt")))throw new IllegalArgumentException("Not an unfinished builder-owned showcase save");
        int floor=level.getMinBuildHeight();
        while(floor<level.getMaxBuildHeight()&&!(level.getBlockEntity(new BlockPos(0,floor+1,-22)) instanceof Case))floor++;
        if(floor>=level.getMaxBuildHeight())throw new IllegalStateException("The original showcase control case is missing");
        Build build=new Build(level,player,actual,floor);build.recover=true;
        try{build.adopt();NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event)->build.tick(event));}
        catch(Throwable error){build.result.completeExceptionally(error);}
        return build.result;
    }

    /** Repair the demonstration's saved model and its firmware, without rebuilding the player base. */
    public static CompletableFuture<Report> repairPrint(ServerLevel level, ServerPlayer player, Path worldDirectory) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("Repair the showcase on its server thread");
        Path actual=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if(!actual.equals(worldDirectory.toAbsolutePath().normalize())||!Files.isRegularFile(actual.resolve("opencomputers/showcase/READY.txt")))throw new IllegalArgumentException("Not the ready showcase save");
        int floor=level.getMinBuildHeight();while(floor<level.getMaxBuildHeight()&&!(level.getBlockEntity(new BlockPos(10,floor+1,5)) instanceof Case))floor++;
        if(floor>=level.getMaxBuildHeight())throw new IllegalStateException("The original fabrication computer is missing");
        PrintRepair repair=new PrintRepair(level,player,actual,floor);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event)->repair.tick(event));return repair.result;
    }

    private static final class PrintRepair {
        final ServerLevel level;final ServerPlayer player;final Path directory;final int floor;
        final CompletableFuture<Report> result=new CompletableFuture<>();
        Machine machine;Container inventory;Object printer;li.cil.oc.api.network.Component device;
        int ticks,phase,started,corrected;
        PrintRepair(ServerLevel level,ServerPlayer player,Path directory,int floor){this.level=level;this.player=player;this.directory=directory;this.floor=floor;}
        li.cil.oc.api.network.Component component(String name){for(var node:machine.node().network().nodes())if(node instanceof li.cil.oc.api.network.Component value&&value.name().equals(name))return value;throw new IllegalStateException("Missing actual fabrication component "+name);}
        void correct(ItemStack stack){
            if(!stack.is(li.cil.oc.api.Items.get("print").item()))return;
            CompoundTag data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();boolean changed=false;
            for(String state:List.of("stateOff","stateOn"))for(Tag raw:data.getList(state,Tag.TAG_COMPOUND)){CompoundTag shape=(CompoundTag)raw;if(shape.getString("texture").equals("minecraft:block/smooth_quartz")){shape.putString("texture","minecraft:block/quartz_block_top");changed=true;}}
            if(changed){stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));corrected+=stack.getCount();}
        }
        void tick(ServerTickEvent.Post event){
            if(result.isDone()||event.getServer()!=level.getServer())return;
            try{
                ticks++;if(ticks>400)throw new IllegalStateException("Fabrication repair timed out in phase "+phase);
                if(phase==0&&ticks>=10){
                    machine=((Case)level.getBlockEntity(new BlockPos(10,floor+1,5))).machine();inventory=(Container)level.getBlockEntity(new BlockPos(22,floor+1,5));
                    printer=inventory.getClass().getMethod("printer").invoke(inventory);device=component("printer3d");machine.stop();phase=1;
                }else if(phase==1&&!machine.isRunning()){
                    device.invoke("reset",machine);
                    if(machine.node() instanceof Connector power)power.changeBuffer(Double.POSITIVE_INFINITY);
                    Object[] written=component("eeprom").invoke("set",machine,FABRICATION.getBytes(StandardCharsets.UTF_8));
                    if(written!=null&&written.length>1&&written[0]==null)throw new IllegalStateException("Cannot update fabrication EEPROM: "+written[1]);
                    Files.writeString(directory.resolve("opencomputers/showcase/programs/fabrication.lua"),FABRICATION,StandardCharsets.UTF_8);machine.host().markChanged();phase=2;
                }else if(phase==2&&!Boolean.TRUE.equals(printer.getClass().getMethod("isPrinting").invoke(printer))){
                    // reset stops new jobs; let the one already paid for finish normally before fixing its item.
                    correct(inventory.getItem(2));inventory.setChanged();
                    for(int slot=0;slot<player.getInventory().getContainerSize();slot++)correct(player.getInventory().getItem(slot));player.getInventory().setChanged();
                    if(!machine.start())throw new IllegalStateException("Fabrication did not reboot: "+machine.lastError());started=ticks;phase=3;
                }else if(phase==3&&ticks-started>=160){
                    if(!machine.isRunning()||machine.lastError()!=null||machine.upTime()<=0)throw new IllegalStateException("Corrected fabrication program failed: "+machine.lastError());
                    Object data=printer.getClass().getField("data").get(printer);CompoundTag preview=new CompoundTag();data.getClass().getMethod("save",CompoundTag.class).invoke(data,preview);
                    boolean quartz=false;for(String state:List.of("stateOff","stateOn"))for(Tag raw:preview.getList(state,Tag.TAG_COMPOUND)){String texture=((CompoundTag)raw).getString("texture");if(texture.equals("minecraft:block/smooth_quartz"))throw new IllegalStateException("Live printer still uses the missing texture");quartz|=texture.equals("minecraft:block/quartz_block_top");}
                    if(!quartz)throw new IllegalStateException("The corrected native printer preview was not rebuilt");
                    correct(inventory.getItem(2));inventory.setChanged();machine.host().markChanged();
                    Files.write(directory.resolve("opencomputers/showcase/PRINT-REPAIR.txt"),List.of("status=PASS","texture=minecraft:block/quartz_block_top","native_preview=true","fabrication_uptime="+machine.upTime(),"corrected_existing_items="+corrected),StandardCharsets.UTF_8);
                    result.complete(new Report(new BlockPos(0,floor+1,24),Map.of("数字制造工坊",new BlockPos(19,floor+1,8)),List.of("fabrication uptime="+machine.upTime()+" corrected native preview")));
                }
            }catch(Throwable error){result.completeExceptionally(new IllegalStateException("Showcase print repair phase="+phase+" tick="+ticks,error));}
        }
    }

    private static final String COMMON = """
        local function p(t) local a=component.list(t)();assert(a,t);return component.proxy(a) end
        local g=p('gpu');local screen=component.list('screen')();g.bind(screen);g.setResolution(50,16)
        g.setBackground(0);g.setForeground(0xD9F4FF);g.fill(1,1,50,16,' ')
        local m=p('modem');m.setStrength(64);m.open(42101)
        local function line(y,s) g.fill(1,y,50,1,' ');g.set(2,y,s) end
        """;
    private static final String DASHBOARD = """
        local address=component.list('gpu')();local g=component.proxy(address);local screen=component.list('screen')();g.bind(screen)
        g.setResolution(80,25);g.setBackground(0x101820);g.setForeground(0xD9F4FF);g.fill(1,1,80,25,' ')
        local address=component.list('modem')();local m=component.proxy(address);m.setStrength(64);m.open(42100)
        local names={'automation','robot','fabrication','geology','drone','mobile','rack'}
        local labels={'物流 / 红石','机器人巡航','全息 / 打印','地质扫描','无人机','移动设备','机架服务器'}
        local states={};local seen={};local entered='';local banner='欢迎来到 OpenComputer 实验基地'
        local function put(x,y,s) g.set(x,y,s) end
        local function draw()
          g.setBackground(0x101820);g.fill(1,1,80,25,' ');g.setForeground(0x51D9E8)
          put(3,2,'OPEN LAB  /  OpenComputer-Reloaded');g.setForeground(0xFFFFFF);put(3,4,banner)
          g.setForeground(0x83A9BB);put(3,6,'工作站                 实时无线遥测');g.setForeground(0xD9F4FF)
          for i,n in ipairs(names) do
            local stale=seen[n] and computer.uptime()-seen[n]>12
            put(3,7+i*2,labels[i]);put(27,7+i*2,stale and '离线 / 已暂停' or states[n] or '等待启动广播')
          end
          g.setForeground(0x62E6AF);put(3,23,'键盘 1 物流启停   2 机器人启停   3 全息启停')
          g.setForeground(0x83A9BB);put(3,24,'运行 '..math.floor(computer.uptime())..' 秒  |  '..entered)
        end
        while true do
          draw();local e={computer.pullSignal(1)}
          if e[1]=='modem_message' and e[4]==42100 then states[e[6]]=tostring(e[7]);seen[e[6]]=computer.uptime() end
          if e[1]=='key_down' then
            if e[3]==49 then m.broadcast(42101,'automation','toggle');banner='已发送物流启停命令'
            elseif e[3]==50 then m.broadcast(42101,'robot','toggle');banner='已发送机器人启停命令'
            elseif e[3]==51 then m.broadcast(42101,'fabrication','toggle');banner='已发送全息启停命令'
            elseif e[3]==8 then entered=entered:sub(1,-2)
            elseif e[3]>=32 and e[3]<127 then entered=(entered..string.char(e[3])):sub(-38) end
          end
        end
        """;
    private static final String AUTOMATION = COMMON + """
        local t=p('transposer');local r=p('redstone');local enabled=true;local moved=0;local pulse=false
        line(2,'物流与红石：真实物品传输');line(4,'左侧输入箱  ->  右侧输出箱')
        line(12,'总控按 1 或在此按空格暂停')
        while true do
          local e={computer.pullSignal(1)}
          if (e[1]=='modem_message' and e[6]=='automation' and e[7]=='toggle') or (e[1]=='key_down' and e[3]==32) then enabled=not enabled end
          if enabled then moved=moved+(t.transferItem(4,5,1) or 0);pulse=not pulse else pulse=false end
          r.setOutput(1,pulse and 15 or 0)
          line(6,'状态：'..(enabled and '运行' or '暂停'));line(8,'实际已搬运：'..moved..' 件')
          line(10,'红石灯：'..(pulse and '亮' or '灭'))
          m.broadcast(42100,'automation',(enabled and '运行 / ' or '暂停 / ')..moved..' 件')
        end
        """;
    private static final String ROBOT = COMMON + """
        local r=p('robot');r.setLightColor(0x55DDFF)
        local enabled=true;local offset=0;local forward=true;local steps=0
        line(2,'Creatix 机器人巡航');line(4,'在玻璃围栏内往返六格')
        line(12,'总控按 2 / 此处空格：启停')
        while true do
          local e={computer.pullSignal(2)}
          if (e[1]=='modem_message' and e[6]=='robot' and e[7]=='toggle') or (e[1]=='key_down' and e[3]==32) then enabled=not enabled end
          if enabled then
            local ok=r.move(forward and 3 or 2)
            if ok then offset=offset+(forward and 1 or -1);steps=steps+1 else forward=not forward end
            if offset>=6 then forward=false elseif offset<=0 then forward=true end
          end
          line(6,'状态：'..(enabled and '巡航' or '暂停'));line(8,'步数：'..steps..'  轨道位置：'..offset)
          m.broadcast(42100,'robot',(enabled and '巡航 / ' or '暂停 / ')..steps..' 步')
        end
        """;
    private static final String FABRICATION = COMMON + """
        local h=p('hologram');local pr=p('printer3d');h.setScale(.55)
        h.setPaletteColor(1,0x48E6FF);h.setPaletteColor(2,0xB17CFF);h.setPaletteColor(3,0xFFD56A)
        local function project()
        h.clear()
        for x=14,34 do h.fill(x,14,1,2,1);h.fill(x,34,1,2,1) end
        for z=14,34 do h.fill(14,z,1,2,1);h.fill(34,z,1,2,1) end
        for y=3,22 do h.set(18,y,18,2);h.set(30,y,18,2);h.set(18,y,30,2);h.set(30,y,30,2) end
        for x=18,30 do for z=18,30 do h.set(x,23,z,3) end;computer.pullSignal(0) end
        end
        project()
        h.setRotationSpeed(12,0,1,0)
        pr.reset();pr.setLabel('基地纪念模型');pr.setTooltip('由实验基地的真实 3D 打印机制造')
        pr.addShape(1,0,1,15,2,15,'minecraft:block/quartz_block_top',false,0xFFFFFF)
        pr.addShape(6,2,6,10,12,10,'minecraft:block/diamond_block',false,0xFFFFFF)
        pr.addShape(3,12,3,13,15,13,'minecraft:block/gold_block',false,0xFFFFFF)
        assert(pr.commit(16));local enabled=true
        line(2,'全息投影与 3D 打印');line(4,'打印机已装入材料和染料')
        line(12,'总控按 3：开关投影');line(14,'打印机右键：领取纪念模型')
        while true do
          local e={computer.pullSignal(2)}
          if e[1]=='modem_message' and e[6]=='fabrication' and e[7]=='toggle' then enabled=not enabled;if enabled then project() else h.clear() end end
          local state,progress=pr.status();line(7,'打印：'..state..'  '..tostring(progress));line(9,'投影：'..(enabled and '开启' or '关闭'))
          m.broadcast(42100,'fabrication','投影'..(enabled and '开' or '关')..' / '..state)
        end
        """;
    private static final String GEOLOGY = COMMON + """
        local geo=p('geolyzer');line(2,'地质扫描：玻璃下的矿石样本')
        line(4,'读取真实 4 x 4 区域的密度')
        while true do
          local d=assert(geo.scan(-2,3,-2,4,4,1));local sum=0
          for i,v in ipairs(d) do sum=sum+v;line(6+math.floor((i-1)/4),string.format('%5.1f  %5.1f  %5.1f  %5.1f',d[math.floor((i-1)/4)*4+1],d[math.floor((i-1)/4)*4+2],d[math.floor((i-1)/4)*4+3],d[math.floor((i-1)/4)*4+4])) end
          line(12,'走到玻璃样本区，查看矿石');line(14,'扫描周期：5 秒')
          m.broadcast(42100,'geology',string.format('16 格 / 密度合计 %.1f',sum));computer.pullSignal(5)
        end
        """;
    private static final String RACK = COMMON + """
        line(2,'机架服务器 / 无线终端');line(4,'这是服务器运行的真实画面')
        line(6,'绑定终端右键可远程访问');line(12,'输入任意英文文字试试')
        local input=''
        while true do
          local e={computer.pullSignal(2)}
          if e[1]=='key_down' then if e[3]==8 then input=input:sub(1,-2) elseif e[3]>=32 and e[3]<127 then input=(input..string.char(e[3])):sub(-42) end end
          line(8,input);line(10,'运行：'..math.floor(computer.uptime())..' 秒')
          m.broadcast(42100,'rack','在线 / 无线终端可用')
        end
        """;
    private static final String MOBILE = COMMON + """
        line(2,'移动设备 / 充电与补给');line(4,'充电器内有可取出的平板')
        line(6,'箱内：平板 / 无人机 / 悬浮靴');line(8,'平板右键打开 OpenOS')
        line(10,'手持分析仪右键设备查看网络');line(12,'桌边的充电器为附近装备供电')
        while true do m.broadcast(42100,'mobile','充电区就绪 / 装备可取');computer.pullSignal(3) end
        """;
    private static final String RACK_POWER = COMMON + """
        line(2,'机架能源与外部总线');line(4,'旁边机架的能源由这里提供')
        line(6,'服务器与终端共用底部总线');line(8,'终端已放在入口补给箱中')
        line(12,'右键机架可查看四层设备')
        while true do computer.pullSignal(3) end
        """;
    private static final String DRONE = """
        local function p(t) local a=component.list(t)();assert(a,t);return component.proxy(a) end
        local d=p('drone');local m=p('modem');m.setStrength(64);d.setLightColor(0xB878FF);d.setStatusText('基地无人机\\n自动巡航')
        local up=true
        while true do d.move(0,up and .7 or -.7,0);up=not up;m.broadcast(42100,'drone','飞行就绪 / 上下巡航');computer.pullSignal(4) end
        """;

    private static final class Build {
        final ServerLevel level; final ServerPlayer player; final UUID playerId; final Path directory;
        final CompletableFuture<Report> result = new CompletableFuture<>();
        final Map<String, BlockPos> stations = new LinkedHashMap<>();
        final Map<String, Machine> machines = new LinkedHashMap<>(); final Map<String, String> sources = new LinkedHashMap<>();
        final Class<? extends Architecture> architecture; final int y;
        Case dashboard, automation, fabrication, geology, mobile, openos; Rack rack; Robot robot; Drone drone;
        Container inputChest, outputChest, supply; ItemStack terminal; BlockPos spawn; int ticks, phase, materialAtBoot, inkAtBoot, outputAtBoot; boolean recover;
        Build(ServerLevel level, ServerPlayer player, Path directory) {
            this(level,player,directory,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0)-1);
        }
        Build(ServerLevel level, ServerPlayer player, Path directory,int floorY) {
            this.level=level;this.player=player;playerId=player.getUUID();this.directory=directory;
            y=floorY;
            architecture=li.cil.oc.api.Machine.architectures().stream().filter(a->"Lua 5.3".equals(li.cil.oc.api.Machine.getArchitectureName(a))).findFirst().orElseThrow();
        }
        BlockPos pos(int x,int dy,int z){return new BlockPos(x,y+dy,z);}
        void set(int x,int dy,int z,BlockState state){level.setBlock(pos(x,dy,z),state,3);}
        void block(int x,int dy,int z,Block block){set(x,dy,z,block.defaultBlockState());}
        ItemStack item(String name){var info=li.cil.oc.api.Items.get(name);if(info==null)throw new IllegalArgumentException("Missing showcase hardware "+name);return info.createItemStack(1);}
        CompoundTag tag(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
        void named(ItemStack stack,String name){stack.set(DataComponents.CUSTOM_NAME,Component.literal(name));}
        @SuppressWarnings({"unchecked","rawtypes"})
        BlockState face(BlockState state,Direction direction){for(Property<?> property:state.getProperties())if((property.getName().equals("facing")||property.getName().equals("yaw"))&&property.getPossibleValues().contains(direction))state=state.setValue((Property)property,direction);return state;}
        void oc(String name,int x,int dy,int z){set(x,dy,z,face(li.cil.oc.api.Items.get(name).block().defaultBlockState(),Direction.SOUTH));}
        void cable(int x,int z){oc("cable",x,1,z);}
        void cableLine(int x0,int x1,int z){for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)cable(x,z);}
        void sign(int x,int z,String a,String b,String c,String d){
            set(x,1,z,Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION,0));
            var sign=(SignBlockEntity)level.getBlockEntity(pos(x,1,z));var text=sign.getFrontText();String[] lines={a,b,c,d};
            for(int i=0;i<4;i++)text=text.setMessage(i,Component.literal(lines[i]));
            sign.setText(text.setColor(DyeColor.CYAN).setHasGlowingText(true),true);sign.setWaxed(true);sign.setChanged();
        }
        void bench(int x0,int x1,int z){for(int x=x0;x<=x1;x++)block(x,1,z,Blocks.SMOOTH_QUARTZ);}
        ItemStack firmware(String name,String source)throws Exception{
            byte[] bytes=source.getBytes(StandardCharsets.UTF_8);if(bytes.length>4096)throw new IllegalArgumentException("EEPROM source exceeds real capacity: "+name+" "+bytes.length);
            sources.put(name,source);return li.cil.oc.api.Items.registerEEPROM("基地 / "+name,bytes,new byte[0],false);
        }
        ItemStack cpu(){ItemStack cpu=item("cpu3");((MutableProcessor)Driver.driverFor(cpu)).setArchitecture(cpu,architecture);return cpu;}
        void insert(Container container,ItemStack stack){for(int slot=0;slot<container.getContainerSize();slot++)if(container.getItem(slot).isEmpty()&&container.canPlaceItem(slot,stack)){container.setItem(slot,stack);return;}throw new IllegalStateException("Showcase host rejected "+stack);}
        Case computer(String label,int x,int z,String source)throws Exception{
            oc("casecreative",x,1,z);Case host=(Case)level.getBlockEntity(pos(x,1,z));
            insert(host,cpu());insert(host,item("ram6"));insert(host,item("ram6"));insert(host,item("graphicscard3"));insert(host,item("wlancard2"));insert(host,item("hdd3"));
            insert(host,source==null?item("luabios"):firmware(label,source));
            oc("screen3",x,2,z);block(x,1,z+1,Blocks.SMOOTH_QUARTZ);oc("keyboard",x,2,z+1);
            machines.put(label,host.machine());return host;
        }
        ItemStack preset(String name,String source)throws Exception{
            ItemStack chosen=ItemStack.EMPTY;
            for(ItemStack stack:CreativeTab.instance.getDisplayItems())if(stack.is(item(name).getItem())&&tag(stack).getInt("oc:tier")==3){chosen=stack.copy();break;}
            if(chosen.isEmpty())throw new IllegalStateException("Configured creative preset missing: "+name);
            CompoundTag data=tag(chosen);String key=name.equals("tablet")?"oc:items":"oc:components";ListTag parts=new ListTag();boolean flashed=false;
            for(Tag raw:data.getList(key,Tag.TAG_COMPOUND)){
                CompoundTag entry=((CompoundTag)raw).copy();ItemStack part=ItemStack.parseOptional(level.registryAccess(),key.equals("oc:items")?entry.getCompound("item"):entry);var driver=Driver.driverFor(part);
                if(driver instanceof MutableProcessor processor)processor.setArchitecture(part,architecture);
                if(source!=null&&driver!=null&&"eeprom".equals(driver.slot(part))){part=firmware(name,source);flashed=true;}
                if(key.equals("oc:items")){entry.put("item",part.saveOptional(level.registryAccess()));parts.add(entry);}else parts.add(part.saveOptional(level.registryAccess()));
            }
            if(source!=null&&!flashed&&!key.equals("oc:items"))parts.add(firmware(name,source).saveOptional(level.registryAccess()));
            data.put(key,parts);chosen.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return chosen;
        }
        void nativePlace(ItemStack stack,BlockPos target,Direction facing){
            float yaw=player.getYRot(),pitch=player.getXRot();ItemStack held=player.getMainHandItem();
            try{player.setYRot(facing.getOpposite().toYRot());player.setXRot(0);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var hit=new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false);
                if(!((BlockItem)stack.getItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit)).consumesAction())throw new IllegalStateException("Native showcase placement failed "+stack);
            }finally{player.setYRot(yaw);player.setXRot(pitch);player.setItemInHand(InteractionHand.MAIN_HAND,held);}
        }
        Container chest(int x,int z,String name){block(x,1,z,Blocks.CHEST);Container result=(Container)level.getBlockEntity(pos(x,1,z));stations.put(name,pos(x,1,z));return result;}
        void stock(Container container,ItemStack...stacks){for(ItemStack stack:stacks){boolean placed=false;for(int slot=0;slot<container.getContainerSize();slot++)if(container.getItem(slot).isEmpty()){container.setItem(slot,stack);placed=true;break;}if(!placed)throw new IllegalStateException("Showcase supplies chest is full");}}
        void adopt()throws Exception{
            dashboard=(Case)level.getBlockEntity(pos(0,1,-22));geology=(Case)level.getBlockEntity(pos(-25,1,-20));openos=(Case)level.getBlockEntity(pos(-25,1,-6));automation=(Case)level.getBlockEntity(pos(-25,1,5));fabrication=(Case)level.getBlockEntity(pos(10,1,5));mobile=(Case)level.getBlockEntity(pos(10,1,16));
            Map<String,Case> hosts=new LinkedHashMap<>();hosts.put("dashboard",dashboard);hosts.put("geology",geology);hosts.put("openos",openos);hosts.put("automation",automation);hosts.put("fabrication",fabrication);hosts.put("mobile",mobile);hosts.put("rack-power",(Case)level.getBlockEntity(pos(15,1,-6)));
            for(var entry:hosts.entrySet())machines.put(entry.getKey(),entry.getValue().machine());
            for(int x=-28;x<=-17;x++)if(level.getBlockEntity(pos(x,1,17)) instanceof Robot found){robot=found;break;}
            if(robot==null)throw new IllegalStateException("The original showcase Robot is missing");machines.put("robot",robot.machine());
            for(Entity entity:level.getEntities().getAll())if(entity instanceof Drone found&&entity.position().distanceToSqr(Vec3.atCenterOf(pos(24,2,19)))<16){drone=found;break;}
            if(drone==null)throw new IllegalStateException("The original showcase Drone is missing");machines.put("drone",drone.machine());
            rack=(Rack)level.getBlockEntity(pos(21,1,-6));inputChest=(Container)level.getBlockEntity(pos(-21,1,5));outputChest=(Container)level.getBlockEntity(pos(-19,1,5));supply=(Container)level.getBlockEntity(pos(-3,1,20));spawn=pos(0,1,24);
            stations.put("总控制室",pos(0,1,-19));stations.put("地质扫描台",pos(-23,1,-17));stations.put("OpenOS 入门桌",pos(-25,1,-4));stations.put("物流与红石",pos(-22,1,8));stations.put("机器人巡航轨道",pos(-22,1,19));stations.put("机架与无线终端",pos(21,1,-4));stations.put("数字制造工坊",pos(19,1,8));stations.put("移动设备与充电",pos(20,1,20));
            sources.put("dashboard",DASHBOARD);sources.put("geology",GEOLOGY);sources.put("automation",AUTOMATION);sources.put("fabrication",FABRICATION);sources.put("mobile",MOBILE);sources.put("rack-power",RACK_POWER);sources.put("robot",ROBOT);sources.put("drone",DRONE);sources.put("rack",RACK);
            // Supply the actual bottom-only projector connection if the first commissioning stopped before it was installed.
            oc("cable",16,0,4);oc("cable",16,0,5);
        }
        void reflash(String name,Machine machine)throws Exception{
            String source=sources.get(name);if(source==null)return;
            if(machine.node() instanceof Connector power)power.changeBuffer(Double.POSITIVE_INFINITY);
            li.cil.oc.api.network.Component eeprom=null;
            for(var node:machine.node().network().nodes())if(node instanceof li.cil.oc.api.network.Component candidate&&candidate.name().equals("eeprom")){eeprom=candidate;break;}
            if(eeprom==null)throw new IllegalStateException("Original writable EEPROM missing: "+name);
            Object[] changed=eeprom.invoke("set",machine,source.getBytes(StandardCharsets.UTF_8));
            if(changed!=null&&changed.length>1&&changed[0]==null)throw new IllegalStateException("Cannot reflash showcase "+name+": "+changed[1]);
            machine.host().markChanged();Files.writeString(directory.resolve("opencomputers/showcase/programs").resolve(name+".lua"),source,StandardCharsets.UTF_8);
        }
        int itemCount(Container inventory){int count=0;for(int slot=0;slot<inventory.getContainerSize();slot++)count+=inventory.getItem(slot).getCount();return count;}
        int printerBudget(boolean ink)throws Exception{
            Container inventory=(Container)level.getBlockEntity(pos(22,1,5));Object printer=invoke(inventory,"printer",new Class<?>[0]);ItemStack stack=inventory.getItem(ink?1:0);
            int perItem=(int)Class.forName("li.cil.oc.common.item.data.PrintData").getMethod(ink?"inkValue":"materialValue",ItemStack.class).invoke(null,stack);
            return printer.getClass().getField(ink?"ink":"material").getInt(printer)+stack.getCount()*perItem;
        }
        void construct()throws Exception{
            Path files=directory.resolve("opencomputers/showcase");if(Files.exists(files.resolve("READY.txt")))throw new IllegalStateException("The persistent showcase already exists");
            level.setDayTime(6000);level.setWeatherParameters(0,0,false,false);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            for(int x=-32;x<=31;x++)for(int z=-24;z<=23;z++){
                for(int dy=1;dy<=10;dy++)level.setBlock(pos(x,dy,z),Blocks.AIR.defaultBlockState(),2);
                boolean aisle=Math.abs(x)<=4||z==-10||z==1||z==12;
                Block material=aisle?Blocks.SMOOTH_STONE:((x+z)&1)==0?Blocks.POLISHED_DIORITE:Blocks.SMOOTH_QUARTZ;
                if(Math.abs(x)==4)material=Blocks.CYAN_CONCRETE;
                if(Math.floorMod(x,8)==0&&Math.floorMod(z,8)==0)material=Blocks.SEA_LANTERN;
                level.setBlock(pos(x,0,z),material.defaultBlockState(),2);
                if(x==-32||x==31||z==-24||z==23){
                    boolean doorway=z==23&&Math.abs(x)<=3;
                    if(!doorway)for(int dy=1;dy<=7;dy++)level.setBlock(pos(x,dy,z),(dy==1||dy==7?Blocks.WHITE_CONCRETE:Blocks.LIGHT_BLUE_STAINED_GLASS).defaultBlockState(),2);
                }
                Block roof=(Math.floorMod(x+32,8)==0||Math.floorMod(z+24,11)==0)?Blocks.WHITE_CONCRETE:Blocks.GLASS;
                level.setBlock(pos(x,8,z),roof.defaultBlockState(),2);
            }
            for(int x:new int[]{-31,-16,0,16,30})for(int z:new int[]{-23,-10,1,12,22})block(x,6,z,Blocks.SEA_LANTERN);
            for(int x:new int[]{-6,6})for(int z=-9;z<=22;z++)if(z!=-5&&z!=6&&z!=17)for(int dy=1;dy<=4;dy++)block(x,dy,z,dy==1?Blocks.WHITE_CONCRETE:Blocks.GLASS);
            for(int z:new int[]{1,12})for(int x=-31;x<=30;x++)if(Math.abs(x)>6)for(int dy=1;dy<=4;dy++)block(x,dy,z,dy==1?Blocks.WHITE_CONCRETE:Blocks.GLASS);
            CreativeTab.instance.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess()));

            dashboard=computer("dashboard",0,-22,DASHBOARD);
            for(int x=-2;x<=2;x++)for(int dy=2;dy<=4;dy++)oc("screen3",x,dy,-22);
            sign(-4,-19,"总控制室","右键屏幕或键盘","键盘 1 / 2 / 3","控制远处真实设备");stations.put("总控制室",pos(0,1,-19));
            for(int x=-8;x<=8;x++)if(Math.abs(x)>2)block(x,1,-22,Blocks.SMOOTH_QUARTZ);

            geology=computer("geology",-25,-20,GEOLOGY);oc("geolyzer",-21,1,-20);cableLine(-25,-21,-21);
            for(int x=-23;x<=-20;x++)for(int z=-17;z<=-14;z++){block(x,-1,z,new Block[]{Blocks.COAL_ORE,Blocks.IRON_ORE,Blocks.COPPER_ORE,Blocks.DIAMOND_ORE}[Math.floorMod(x+z,4)]);block(x,0,z,Blocks.GLASS);}
            sign(-16,-18,"地质扫描台","玻璃下有矿石样本","屏幕实时显示密度","读取真实 4×4 区域");stations.put("地质扫描台",pos(-23,1,-17));

            bench(-29,-15,-6);openos=computer("openos",-25,-6,null);oc("diskdrive",-22,1,-6);cableLine(-25,-22,-7);
            ((Container)level.getBlockEntity(pos(-22,1,-6))).setItem(0,item("openos"));
            Container library=chest(-17,-6,"系统与入门资料");stock(library,item("openos"),item("plan9k"),item("manual"),item("luabios"),item("hdd3"),item("floppy"),item("analyzer"));
            sign(-11,-5,"OpenOS 入门桌","右键键盘开始输入","help 查看命令","install 安装到硬盘");stations.put("OpenOS 入门桌",pos(-25,1,-4));

            automation=computer("automation",-25,5,AUTOMATION);cableLine(-25,-16,4);oc("transposer",-20,1,5);oc("redstone",-16,1,5);block(-16,2,5,Blocks.REDSTONE_LAMP);
            inputChest=chest(-21,5,"物流输入箱");outputChest=chest(-19,5,"物流输出箱");stock(inputChest,new ItemStack(Items.IRON_INGOT,64),new ItemStack(Items.COPPER_INGOT,64),new ItemStack(Items.REDSTONE,64),new ItemStack(Items.GOLD_INGOT,64));
            sign(-11,5,"物流与红石","左箱每秒搬一件","右箱查看真实输出","总控 1 / 空格启停");stations.put("物流与红石",pos(-22,1,8));

            for(int x=-29;x<=-16;x++)for(int z=14;z<=21;z++){block(x,0,z,z==17?Blocks.CYAN_CONCRETE:Blocks.IRON_BLOCK);if(x==-29||x==-16||z==14||z==21)block(x,1,z,Blocks.GLASS);}
            nativePlace(preset("robot",ROBOT),pos(-27,1,17),Direction.EAST);robot=(Robot)level.getBlockEntity(pos(-27,1,17));robot.equipmentInventory().setItem(0,new ItemStack(Items.IRON_PICKAXE));machines.put("robot",robot.machine());
            sign(-11,16,"机器人巡航轨道","真实机器人往返六格","右键查看程序画面","总控 2 / 空格启停");stations.put("机器人巡航轨道",pos(-22,1,19));

            bench(9,28,-6);oc("rack",21,1,-6);rack=(Rack)level.getBlockEntity(pos(21,1,-6));rack.setItem(0,item("servercreative"));rack.setItem(1,item("terminalserver"));rack.setItem(2,item("diskdrivemountable"));
            // The adjacent creative case supplies the rack's real external energy bus.
            computer("rack-power",15,-6,RACK_POWER);cableLine(15,21,-7);
            oc("raid",25,1,-6);for(int slot=0;slot<3;slot++)((Container)level.getBlockEntity(pos(25,1,-6))).setItem(slot,item("hdd3"));cable(25,-7);cableLine(21,25,-7);
            sign(10,-4,"机架与无线终端","四层机架可右键操作","终端已放在入口箱中","RAID 已装三块硬盘");stations.put("机架与无线终端",pos(21,1,-4));

            fabrication=computer("fabrication",10,5,FABRICATION);cableLine(10,28,4);oc("hologram2",16,1,5);oc("printer",22,1,5);oc("assembler",28,1,5);oc("disassembler",28,1,8);for(int z=5;z<=8;z++)cable(29,z);cable(29,4);
            // The original hologram accepts network/energy only through its bottom face.
            oc("cable",16,0,4);oc("cable",16,0,5);
            Container printer=(Container)level.getBlockEntity(pos(22,1,5));printer.setItem(0,item("chamelium").copyWithCount(64));printer.setItem(1,new ItemStack(Items.BLACK_DYE,64));
            Container assembler=(Container)level.getBlockEntity(pos(28,1,5));assembler.setItem(0,item("case1"));assembler.setItem(16,item("cpu1"));assembler.setItem(17,item("ram1"));
            ((Container)level.getBlockEntity(pos(28,1,8))).setItem(0,item("graphicscard1"));
            Container parts=chest(25,9,"制造材料箱");stock(parts,item("chamelium").copyWithCount(64),new ItemStack(Items.BLACK_DYE,64),item("case1"),item("cpu1"),item("ram1"),item("luabios"),item("graphicscard1"));
            sign(10,9,"数字制造工坊","全息模型正在旋转","打印机可领取纪念品","装配机已备好硬件");stations.put("数字制造工坊",pos(19,1,8));

            mobile=computer("mobile",10,16,MOBILE);cableLine(10,25,15);oc("charger",17,1,16);oc("capacitor",18,1,15);oc("carpetedcapacitor",19,1,15);oc("powerdistributor",20,1,15);oc("powerconverter",21,1,15);
            block(17,1,17,Blocks.REDSTONE_BLOCK);((Container)level.getBlockEntity(pos(17,1,16))).setItem(0,preset("tablet",null));
            block(24,1,19,Blocks.IRON_BLOCK);ItemStack droneStack=preset("drone",DRONE);ItemStack old=player.getMainHandItem();player.setItemInHand(InteractionHand.MAIN_HAND,droneStack);
            try{if(!droneStack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos(24,1,19)).add(0,.5,0),Direction.UP,pos(24,1,19),false))).consumesAction())throw new IllegalStateException("Native showcase drone placement failed");}
            finally{player.setItemInHand(InteractionHand.MAIN_HAND,old);}
            for(Entity entity:level.getEntities().getAll())if(entity instanceof Drone value&&entity.position().distanceToSqr(Vec3.atCenterOf(pos(24,2,19)))<9){drone=value;machines.put("drone",drone.machine());break;}
            if(drone==null)throw new IllegalStateException("Actual showcase drone missing");
            Container equipment=chest(12,20,"移动装备箱");stock(equipment,preset("tablet",null),preset("drone",DRONE),preset("robot",null),item("hoverboots"),item("analyzer"),item("navigationupgrade"),item("chunkloaderupgrade"));
            sign(10,19,"移动设备与充电","附近装备自动充电","平板右键运行 OpenOS","箱中设备可自由取用");stations.put("移动设备与充电",pos(20,1,20));

            supply=chest(-3,20,"入口补给箱");stock(supply,item("manual"),item("analyzer"),item("luabios"),item("openos"),item("plan9k"),item("hdd3"),item("floppy"),item("keyboard"),item("screen3"),item("cable").copyWithCount(64),item("casecreative"),cpu(),item("ram6"),item("graphicscard3"));
            sign(0,21,"欢迎来到 OPEN LAB","从中央通道向前走","左右共有八个体验区","入口箱内有手册补给");
            spawn=pos(0,1,24);level.setDefaultSpawnPos(spawn,180);player.connection.teleport(.5,y+1,21.5,180,0);player.getAbilities().mayfly=true;player.getAbilities().flying=false;player.onUpdateAbilities();
            player.getInventory().add(item("manual"));player.getInventory().add(item("analyzer"));player.getInventory().add(preset("tablet",null));player.getInventory().add(item("openos"));player.getInventory().add(item("hdd3"));
            Files.createDirectories(files.resolve("programs"));
            for(var source:sources.entrySet())Files.writeString(files.resolve("programs").resolve(source.getKey()+".lua"),source.getValue(),StandardCharsets.UTF_8);
        }
        Object invoke(Object target,String method,Class<?>[] types,Object...args)throws Exception{return target.getClass().getMethod(method,types).invoke(target,args);}
        void commissionRack()throws Exception{
            if(!(rack.getMountable(0) instanceof li.cil.oc.api.internal.Server server)||rack.getMountable(1)==null||rack.getMountable(2)==null)throw new IllegalStateException("Actual rack mountables not initialized");
            Container inventory=(Container)server;
            if(!recover){insert(inventory,cpu());insert(inventory,item("ram6"));insert(inventory,item("graphicscard3"));insert(inventory,item("wlancard2"));insert(inventory,firmware("rack",RACK));insert(inventory,item("hdd3"));}
            // The rack balances power between external sides without joining their networks.
            // Keep the blade/terminal screen on the bottom bus, separate from the supply desk.
            for(int slot=0;slot<3;slot++)invoke(rack,"connect",new Class<?>[]{int.class,int.class,int.class},slot,0,Direction.DOWN.ordinal());
            if(!recover){
                ((Container)rack.getMountable(2)).setItem(0,item("openos"));
                terminal=item("terminal");if(!rack.getMountable(1).onActivate(player,InteractionHand.MAIN_HAND,terminal,.5f,.5f))throw new IllegalStateException("Native showcase terminal binding failed");
                named(terminal,"基地 / 已绑定无线终端");stock(supply,terminal.copy());player.getInventory().add(terminal.copy());
            }
            machines.put("rack",server.machine());
            Files.writeString(directory.resolve("opencomputers/showcase/programs/rack.lua"),RACK,StandardCharsets.UTF_8);
        }
        void tick(ServerTickEvent.Post event){
            if(result.isDone()||event.getServer()!=level.getServer())return;
            try{
                ticks++;
                if(ticks==40){commissionRack();if(recover){for(var entry:machines.entrySet())if(sources.containsKey(entry.getKey()))entry.getValue().stop();for(var entry:machines.entrySet())reflash(entry.getKey(),entry.getValue());}phase=1;}
                if(ticks==60){
                    materialAtBoot=printerBudget(false);inkAtBoot=printerBudget(true);outputAtBoot=itemCount(outputChest);
                    for(var entry:machines.entrySet()){
                        Machine machine=entry.getValue();if(machine.node() instanceof Connector power)power.changeBuffer(Double.POSITIVE_INFINITY);
                        if(recover&&entry.getKey().equals("openos")&&machine.isRunning())continue;
                        if(!machine.start())throw new IllegalStateException("Showcase boot failed: "+entry.getKey()+" "+machine.lastError());
                    }
                    phase=2;
                }
                if(ticks==200){
                    List<String> online=new ArrayList<>();for(var entry:machines.entrySet()){
                        Machine machine=entry.getValue();if(!machine.isRunning()||machine.lastError()!=null||machine.upTime()<=0)throw new IllegalStateException("Showcase program failed: "+entry.getKey()+" "+machine.lastError());online.add(entry.getKey()+" uptime="+machine.upTime());
                    }
                    if(itemCount(outputChest)<=outputAtBoot)throw new IllegalStateException("Showcase transposer did not transfer actual items after commissioning");
                    if(printerBudget(false)>=materialAtBoot||printerBudget(true)>=inkAtBoot)throw new IllegalStateException("Showcase printer did not consume real material/ink after commissioning");
                    List<String> lines=new ArrayList<>(List.of("OpenComputer-Reloaded 实验基地","入口: "+spawn,"总控制室键盘: 1 物流启停 / 2 机器人启停 / 3 全息启停","OpenOS 入门桌: help 查看命令，install 安装系统到硬盘","所有正在工作的示例均由游戏内 Lua 计算机驱动。"));
                    stations.forEach((name,pos)->lines.add(name+": "+pos));lines.addAll(online);
                    Files.write(directory.resolve("opencomputers/showcase/READY.txt"),lines,StandardCharsets.UTF_8);
                    for(Machine machine:machines.values())machine.host().markChanged();
                    level.getServer().getPlayerList().getPlayer(playerId).sendSystemMessage(Component.literal("实验基地已就绪。中央总控按 1 / 2 / 3，入口箱中可取手册与补给。"));
                    result.complete(new Report(spawn,Map.copyOf(stations),List.copyOf(online)));phase=3;
                }
            }catch(Throwable error){result.completeExceptionally(new IllegalStateException("Showcase commissioning phase="+phase+" tick="+ticks,error));}
        }
    }
}
