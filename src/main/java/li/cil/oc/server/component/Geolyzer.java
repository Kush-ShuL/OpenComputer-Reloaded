package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.event.GeolyzerEvent;
import li.cil.oc.api.internal.Database;
import li.cil.oc.api.machine.*;
import li.cil.oc.api.network.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.*;

/** The upstream geolyzer callbacks and vanilla scan/analyze event handlers. */
@EventBusSubscriber(modid="opencomputers")
public final class Geolyzer extends WorldComponent implements DeviceInfo {
    public Geolyzer(EnvironmentHost host){super(host);setNode(li.cil.oc.api.Network.newNode(this,Visibility.Network).withComponent("geolyzer").withConnector().create());}
    @Override public Map<String,String> getDeviceInfo(){return Map.of("class","generic","description","Geolyzer","vendor","MightyPirates GmbH & Co. KG","product","Terrain Analyzer MkII","capacity",Integer.toString(Settings.get().geolyzerRange));}
    private boolean charge(){return ((Connector)node()).tryChangeBuffer(-Settings.get().geolyzerScanCost);}
    private BlockPos controlPosition(){return host instanceof li.cil.oc.api.internal.Drone&&host instanceof Entity entity?BlockPos.containing(entity.getX(),entity.getY()+.5,entity.getZ()):position();}
    @Override protected Player fakePlayer(){Player player=super.fakePlayer();BlockPos position=controlPosition();player.setPos(position.getX()+.5,position.getY()+.5,position.getZ()+.5);return player;}
    private Direction controlSide(Arguments args){
        int index=args.checkInteger(0);if(index<0||index>5)throw new IllegalArgumentException("invalid side");Direction side=Direction.from3DDataValue(index);
        if(host instanceof li.cil.oc.api.internal.Microcontroller microcontroller)return microcontroller.toLocal(side);
        if(host instanceof li.cil.oc.api.internal.Robot||host instanceof li.cil.oc.api.internal.Drone||host instanceof li.cil.oc.api.internal.Tablet)return ((li.cil.oc.api.internal.Rotatable)host).toGlobal(side);
        return side;
    }
    private boolean sky(){return !host.world().dimensionType().ultraWarm()&&host.world().canSeeSkyFromBelowWater(controlPosition().above());}
    @Callback(doc="function():boolean -- Returns whether there is a clear line of sight to the sky directly above.") public Object[] canSeeSky(Context context,Arguments args){return new Object[]{sky()};}
    @Callback(doc="function():boolean -- Return whether the sun is currently visible directly above.") public Object[] isSunVisible(Context context,Arguments args){var world=host.world();boolean canRain=world.getBiome(position().above()).value().getPrecipitationAt(position().above())==net.minecraft.world.level.biome.Biome.Precipitation.RAIN;return new Object[]{world.isDay()&&sky()&&(!canRain||!world.isRaining()&&!world.isThundering())};}
    @Callback(doc="function(x:number, z:number[, y:number, w:number, d:number, h:number][, ignoreReplaceable:boolean|options:table]):table -- Analyzes the density of the column at the specified relative coordinates.") public Object[] scan(Context context,Arguments args){
        int minX=args.checkInteger(0),minZ=args.checkInteger(1),minY=-32,maxX=minX,maxZ=minZ,maxY=31,optionIndex=2;
        if(args.count()>5&&args.isInteger(2)&&args.isInteger(3)&&args.isInteger(4)&&args.isInteger(5)){minY=args.checkInteger(2);maxX=Math.addExact(minX,args.checkInteger(3)-1);maxZ=Math.addExact(minZ,args.checkInteger(4)-1);maxY=Math.addExact(minY,args.checkInteger(5)-1);int a=Math.min(minX,maxX),b=Math.min(minY,maxY),c=Math.min(minZ,maxZ);maxX=Math.max(minX,maxX);maxY=Math.max(minY,maxY);maxZ=Math.max(minZ,maxZ);minX=a;minY=b;minZ=c;optionIndex=6;}
        if(((long)maxX-minX+1)*((long)maxY-minY+1)*((long)maxZ-minZ+1)>64)throw new IllegalArgumentException("volume too large (maximum is 64)");
        Map<?,?> options=args.isBoolean(optionIndex)?Map.of("includeReplaceable",!args.checkBoolean(optionIndex)):args.optTable(optionIndex,Map.of());int range=Settings.get().geolyzerRange;
        for(int coordinate:new int[]{minX,minY,minZ,maxX,maxY,maxZ})if(Math.abs((long)coordinate)>range)throw new IllegalArgumentException("location out of bounds");
        if(!charge())return new Object[]{null,"not enough energy"};var event=new GeolyzerEvent.Scan(host,options,minX,minY,minZ,maxX,maxY,maxZ);NeoForge.EVENT_BUS.post(event);return event.isCanceled()?new Object[]{null,"scan was canceled"}:new Object[]{event.data};
    }
    @Callback(doc="function(side:number[,options:table]):table -- Get some information on a directly adjacent block.") public Object[] analyze(Context context,Arguments args){if(!Settings.get().allowItemStackInspection)return new Object[]{null,"not enabled in config"};Direction direction=side(args,0);Map<?,?> options=args.optTable(1,Map.of());if(!charge())return new Object[]{null,"not enough energy"};var event=new GeolyzerEvent.Analyze(host,options,position().relative(direction));NeoForge.EVENT_BUS.post(event);return event.isCanceled()?new Object[]{null,"scan was canceled"}:new Object[]{event.data};}
    @Callback(doc="function(side:number, dbAddress:string, dbSlot:number):boolean -- Store an item stack representation of the block on the specified side in a database component.") public Object[] store(Context context,Arguments args){Direction direction=side(args,0);if(!charge())return new Object[]{null,"not enough energy"};Item item=host.world().getBlockState(position().relative(direction)).getBlock().asItem();if(item==Items.AIR)return new Object[]{null,"block has no registered item representation"};Node target=node().network()==null?null:node().network().node(args.checkString(1));if(!(target instanceof Component))throw new IllegalArgumentException("no such component");if(!(target.host() instanceof Database database))throw new IllegalArgumentException("not a database");int index=slot(args,2,database.size());ItemStack old=database.getStackInSlot(index);database.setStackInSlot(index,new ItemStack(item));return new Object[]{old!=ItemStack.EMPTY};}
    @Callback(doc="function(side:number):boolean, string -- Checks the contents of the block on the specified sides and returns the findings.") public Object[] detect(Context context,Arguments args){
        BlockPos pos=controlPosition().relative(controlSide(args));Player player=fakePlayer();Entity nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(Entity entity:host.world().getEntities(player,new AABB(pos),entity->!entity.isSpectator())){double candidate=entity.distanceToSqr(player);if(candidate<distance){nearest=entity;distance=candidate;}}
        if(nearest instanceof LivingEntity||nearest instanceof AbstractMinecart)return new Object[]{true,"entity"};
        var state=host.world().getBlockState(pos);if(state.isAir())return new Object[]{false,"air"};if(state.getBlock() instanceof LiquidBlock||state.canBeReplaced()){var event=new BlockEvent.BreakEvent(host.world(),pos,state,player);NeoForge.EVENT_BUS.post(event);return new Object[]{event.isCanceled(),state.getBlock() instanceof LiquidBlock?"liquid":"replaceable"};}return new Object[]{true,state.getCollisionShape(host.world(),pos).isEmpty()?"passable":"solid"};
    }
    @Override public void onMessage(Message message){super.onMessage(message);TabletUseData use=TabletUseData.from(message);if(use!=null&&charge()){var event=new GeolyzerEvent.Analyze(host,Map.of(),use.position());NeoForge.EVENT_BUS.post(event);if(!event.isCanceled())event.data.forEach((key,value)->{if(value instanceof Number number)use.result().putDouble(key,number.doubleValue());else if(value instanceof String string&&!string.isEmpty())use.result().putString(key,string);});}}
    @SubscribeEvent public static void scanVanilla(GeolyzerEvent.Scan event){
        if(!li.cil.oc.common.integration.VanillaDrivers.integrationEnabled("minecraft"))return;
        var world=event.host.world();BlockPos origin=BlockPos.containing(event.host.xPosition(),event.host.yPosition(),event.host.zPosition());boolean replaceable=!Boolean.FALSE.equals(event.options.get("includeReplaceable"));int width=event.maxX-event.minX+1,depth=event.maxZ-event.minZ+1;
        // RandomSource has no nextBytes; preserve java.util.Random's original four-byte extraction order.
        for(int i=0;i<event.data.length;){int bits=world.random.nextInt();int end=Math.min(i+4,event.data.length);for(;i<end;bits>>=8)event.data[i++]=(byte)bits/128f/33f;}
        for(int y=event.minY;y<=event.maxY;y++)for(int z=event.minZ;z<=event.maxZ;z++)for(int x=event.minX;x<=event.maxX;x++){
            int index=x-event.minX+((z-event.minZ)+(y-event.minY)*depth)*width;BlockPos pos=origin.offset(x,y,z);if(!world.hasChunkAt(pos)||world.isOutsideBuildHeight(pos)){event.data[index]=0;continue;}var state=world.getBlockState(pos);if(!state.isAir()&&(replaceable||!state.getFluidState().isEmpty()||!state.canBeReplaced()))event.data[index]=event.data[index]*(float)Math.sqrt(x*x+y*y+z*z)*Settings.get().geolyzerNoise+state.getDestroySpeed(world,pos);else event.data[index]=0;
        }
    }
    @SubscribeEvent public static void analyzeVanilla(GeolyzerEvent.Analyze event){
        if(!li.cil.oc.common.integration.VanillaDrivers.integrationEnabled("minecraft"))return;
        var world=event.host.world();if(!world.hasChunkAt(event.pos))return;var state=world.getBlockState(event.pos);Block block=state.getBlock();event.data.put("name",BuiltInRegistries.BLOCK.getKey(block));event.data.put("hardness",state.getDestroySpeed(world,event.pos));
        event.data.put("harvestLevel",state.is(BlockTags.NEEDS_DIAMOND_TOOL)?3:state.is(BlockTags.NEEDS_IRON_TOOL)?2:state.is(BlockTags.NEEDS_STONE_TOOL)?1:0);event.data.put("harvestTool",state.is(BlockTags.MINEABLE_WITH_PICKAXE)?"pickaxe":state.is(BlockTags.MINEABLE_WITH_AXE)?"axe":state.is(BlockTags.MINEABLE_WITH_SHOVEL)?"shovel":state.is(BlockTags.MINEABLE_WITH_HOE)?"hoe":null);event.data.put("color",state.getMapColor(world,event.pos).col);event.data.put("metadata",0);Map<String,Object> properties=new HashMap<>();state.getValues().forEach((property,value)->properties.put(property.getName(),value));event.data.put("properties",properties);if(Settings.get().insertIdsInConverters)event.data.put("id",BuiltInRegistries.BLOCK.getId(block));
        if(block instanceof net.minecraft.world.level.block.CropBlock||block instanceof net.minecraft.world.level.block.StemBlock||block instanceof net.minecraft.world.level.block.CocoaBlock||block instanceof net.minecraft.world.level.block.NetherWartBlock||block instanceof net.minecraft.world.level.block.ChorusFlowerBlock)
            for(var property:state.getProperties())if(property instanceof IntegerProperty age&&property.getName().equals("age")){int maximum=age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);if(maximum>0)event.data.put("growth",Math.max(0,Math.min(1,(float)state.getValue(age)/maximum)));break;}
        if(block instanceof net.minecraft.world.level.block.AttachedStemBlock||block==net.minecraft.world.level.block.Blocks.MELON||block==net.minecraft.world.level.block.Blocks.PUMPKIN||block==net.minecraft.world.level.block.Blocks.CARVED_PUMPKIN||block==net.minecraft.world.level.block.Blocks.CACTUS||block==net.minecraft.world.level.block.Blocks.SUGAR_CANE||block==net.minecraft.world.level.block.Blocks.CHORUS_PLANT)event.data.put("growth",1f);
    }
}
