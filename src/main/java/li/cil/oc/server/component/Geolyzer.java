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
    private boolean sky(){return !host.world().dimensionType().ultraWarm()&&host.world().canSeeSky(position().above());}
    @Callback(doc="function():boolean -- Check for a clear view of the sky.") public Object[] canSeeSky(Context context,Arguments args){return new Object[]{sky()};}
    @Callback(doc="function():boolean -- Check whether the sun is visible above.") public Object[] isSunVisible(Context context,Arguments args){var world=host.world();return new Object[]{world.isDay()&&sky()&&(!world.getBiome(position().above()).value().hasPrecipitation()||!world.isRaining()&&!world.isThundering())};}
    @Callback(doc="function(x:number,z:number[,y:number,w:number,d:number,h:number][,ignoreReplaceable:boolean|options:table]):table -- Scan terrain density.") public Object[] scan(Context context,Arguments args){
        int minX=args.checkInteger(0),minZ=args.checkInteger(1),minY=-32,maxX=minX,maxZ=minZ,maxY=31,optionIndex=2;
        if(args.count()>5&&args.isInteger(2)&&args.isInteger(3)&&args.isInteger(4)&&args.isInteger(5)){minY=args.checkInteger(2);maxX=Math.addExact(minX,args.checkInteger(3)-1);maxZ=Math.addExact(minZ,args.checkInteger(4)-1);maxY=Math.addExact(minY,args.checkInteger(5)-1);int a=Math.min(minX,maxX),b=Math.min(minY,maxY),c=Math.min(minZ,maxZ);maxX=Math.max(minX,maxX);maxY=Math.max(minY,maxY);maxZ=Math.max(minZ,maxZ);minX=a;minY=b;minZ=c;optionIndex=6;}
        if(((long)maxX-minX+1)*((long)maxY-minY+1)*((long)maxZ-minZ+1)>64)throw new IllegalArgumentException("volume too large (maximum is 64)");int range=Settings.get().geolyzerRange;
        for(int coordinate:new int[]{minX,minY,minZ,maxX,maxY,maxZ})if(Math.abs((long)coordinate)>range)throw new IllegalArgumentException("location out of bounds");
        Map<?,?> options=args.isBoolean(optionIndex)?Map.of("includeReplaceable",!args.checkBoolean(optionIndex)):args.optTable(optionIndex,Map.of());if(!charge())return new Object[]{null,"not enough energy"};var event=new GeolyzerEvent.Scan(host,options,minX,minY,minZ,maxX,maxY,maxZ);NeoForge.EVENT_BUS.post(event);return event.isCanceled()?new Object[]{null,"scan was canceled"}:new Object[]{event.data};
    }
    @Callback(doc="function(side:number[,options:table]):table -- Analyze an adjacent block.") public Object[] analyze(Context context,Arguments args){if(!Settings.get().allowItemStackInspection)return new Object[]{null,"not enabled in config"};Direction direction=side(args,0);if(!charge())return new Object[]{null,"not enough energy"};var event=new GeolyzerEvent.Analyze(host,args.optTable(1,Map.of()),position().relative(direction));NeoForge.EVENT_BUS.post(event);return event.isCanceled()?new Object[]{null,"scan was canceled"}:new Object[]{event.data};}
    @Callback(doc="function(side:number,dbAddress:string,dbSlot:number):boolean -- Store a block's item representation in a database.") public Object[] store(Context context,Arguments args){Direction direction=side(args,0);if(!charge())return new Object[]{null,"not enough energy"};Item item=host.world().getBlockState(position().relative(direction)).getBlock().asItem();if(item==Items.AIR)return new Object[]{null,"block has no registered item representation"};Node target=node().network()==null?null:node().network().node(args.checkString(1));if(target==null||!target.canBeReachedFrom(node()))throw new IllegalArgumentException("no such component");if(!(target.host() instanceof Database database))throw new IllegalArgumentException("not a database");int index=slot(args,2,database.size());ItemStack old=database.getStackInSlot(index);database.setStackInSlot(index,new ItemStack(item));return new Object[]{old!=null&&!old.isEmpty()};}
    @Callback(doc="function(side:number):boolean,string -- Detect an adjacent block or entity.") public Object[] detect(Context context,Arguments args){BlockPos pos=position().relative(side(args,0));for(var entity:host.world().getEntities((net.minecraft.world.entity.Entity)null,new AABB(pos),entity->entity.isAlive()&&(entity instanceof LivingEntity||entity instanceof AbstractMinecart)))if(entity!=fakePlayer())return new Object[]{true,"entity"};var state=host.world().getBlockState(pos);if(state.isAir())return new Object[]{false,"air"};if(state.getBlock() instanceof LiquidBlock||state.canBeReplaced()){var event=new BlockEvent.BreakEvent(host.world(),pos,state,fakePlayer());NeoForge.EVENT_BUS.post(event);return new Object[]{event.isCanceled(),state.getBlock() instanceof LiquidBlock?"liquid":"replaceable"};}return new Object[]{true,state.getCollisionShape(host.world(),pos).isEmpty()?"passable":"solid"};}
    @Override public void onMessage(Message message){super.onMessage(message);if(message.name().equals("tablet.use")&&message.data().length>=4&&message.data()[0] instanceof CompoundTag tag&&message.data()[3] instanceof BlockPos pos&&charge()){var event=new GeolyzerEvent.Analyze(host,Map.of(),pos);NeoForge.EVENT_BUS.post(event);if(!event.isCanceled())event.data.forEach((key,value)->{if(value instanceof Number number)tag.putDouble(key,number.doubleValue());else if(value instanceof String string&&!string.isEmpty())tag.putString(key,string);});}}
    @SubscribeEvent public static void scanVanilla(GeolyzerEvent.Scan event){
        if(!li.cil.oc.common.integration.VanillaDrivers.integrationEnabled("minecraft"))return;
        var world=event.host.world();BlockPos origin=BlockPos.containing(event.host.xPosition(),event.host.yPosition(),event.host.zPosition());boolean replaceable=!Boolean.FALSE.equals(event.options.get("includeReplaceable"));int width=event.maxX-event.minX+1,depth=event.maxZ-event.minZ+1;
        for(int y=event.minY;y<=event.maxY;y++)for(int z=event.minZ;z<=event.maxZ;z++)for(int x=event.minX;x<=event.maxX;x++){
            int index=x-event.minX+((z-event.minZ)+(y-event.minY)*depth)*width;BlockPos pos=origin.offset(x,y,z);float noise=(byte)world.random.nextInt(256)/128f/33f;event.data[index]=0;if(!world.hasChunkAt(pos)||world.isOutsideBuildHeight(pos))continue;var state=world.getBlockState(pos);if(!state.isAir()&&(replaceable||!state.getFluidState().isEmpty()||!state.canBeReplaced()))event.data[index]=state.getDestroySpeed(world,pos)+noise*(float)Math.sqrt(x*x+y*y+z*z)*Settings.get().geolyzerNoise;
        }
    }
    @SubscribeEvent public static void analyzeVanilla(GeolyzerEvent.Analyze event){
        if(!li.cil.oc.common.integration.VanillaDrivers.integrationEnabled("minecraft"))return;
        var world=event.host.world();if(!world.hasChunkAt(event.pos))return;var state=world.getBlockState(event.pos);Block block=state.getBlock();event.data.put("name",BuiltInRegistries.BLOCK.getKey(block).toString());event.data.put("hardness",state.getDestroySpeed(world,event.pos));
        event.data.put("harvestLevel",state.is(BlockTags.NEEDS_DIAMOND_TOOL)?3:state.is(BlockTags.NEEDS_IRON_TOOL)?2:state.is(BlockTags.NEEDS_STONE_TOOL)?1:0);event.data.put("harvestTool",state.is(BlockTags.MINEABLE_WITH_PICKAXE)?"pickaxe":state.is(BlockTags.MINEABLE_WITH_AXE)?"axe":state.is(BlockTags.MINEABLE_WITH_SHOVEL)?"shovel":state.is(BlockTags.MINEABLE_WITH_HOE)?"hoe":null);event.data.put("color",state.getMapColor(world,event.pos).col);event.data.put("metadata",0);Map<String,Object> properties=new HashMap<>();state.getValues().forEach((property,value)->properties.put(property.getName(),value));event.data.put("properties",properties);if(Settings.get().insertIdsInConverters)event.data.put("id",BuiltInRegistries.BLOCK.getId(block));
        if(block instanceof net.minecraft.world.level.block.CropBlock||block instanceof net.minecraft.world.level.block.StemBlock||block instanceof net.minecraft.world.level.block.CocoaBlock||block instanceof net.minecraft.world.level.block.NetherWartBlock||block instanceof net.minecraft.world.level.block.ChorusFlowerBlock)
            for(var property:state.getProperties())if(property instanceof IntegerProperty age&&property.getName().equals("age")){int maximum=age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);if(maximum>0)event.data.put("growth",Math.max(0,Math.min(1,(float)state.getValue(age)/maximum)));break;}
        if(block==net.minecraft.world.level.block.Blocks.MELON||block==net.minecraft.world.level.block.Blocks.PUMPKIN||block==net.minecraft.world.level.block.Blocks.CACTUS||block==net.minecraft.world.level.block.Blocks.SUGAR_CANE||block==net.minecraft.world.level.block.Blocks.CHORUS_PLANT)event.data.put("growth",1f);
    }
}
