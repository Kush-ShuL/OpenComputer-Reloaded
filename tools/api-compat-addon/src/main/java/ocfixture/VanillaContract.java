package ocfixture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import li.cil.oc.api.Items;
import li.cil.oc.api.Network;
import li.cil.oc.api.event.GeolyzerEvent;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Actual callback conversions and vanilla event/provider gates in an isolated world. */
public final class VanillaContract {
    private static Run active;
    public static void start(ServerLevel level, BlockPos pos, List<String> results) throws Exception {
        active = new Run(level, pos, results);
        try { active.start(); } catch (Exception | Error e) { active.cleanup(); active = null; throw e; }
    }
    public static boolean isRunning() { return active != null; }
    public static void tick() {
        if (active == null || ++active.ticks < 5) return;
        Run run = active;
        try { run.verify(); } catch (Throwable e) { run.results.add("FAIL I12.vanilla: " + e); }
        finally { run.cleanup(); active = null; }
    }
    public static void close() { if (active != null) { active.cleanup(); active = null; } }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static String originalId(long seed, int id) throws Exception {
        byte[] bytes = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putLong(seed).putInt(id).array();
        return UUID.nameUUIDFromBytes(MessageDigest.getInstance("MD5").digest(bytes)).toString();
    }
    private static final class Run {
        final ServerLevel level; final BlockPos pos; final List<String> results;
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final List<Long> forced = new ArrayList<>();
        final boolean enabled = li.cil.oc.api.API.config.getStringList("integration.modBlacklist").stream().noneMatch(s -> s.trim().equalsIgnoreCase("minecraft"));
        Case machineHost; Probe probe; Component io; int ticks;
        Run(ServerLevel level, BlockPos pos, List<String> results) { this.level=level; this.pos=pos; this.results=results; }
        void place(BlockPos p, BlockState state) {
            if (level.setChunkForced(p.getX() >> 4,p.getZ() >> 4,true)) forced.add(net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4,p.getZ() >> 4));
            level.getChunkAt(p); require(level.isEmptyBlock(p), "Vanilla fixture refuses an occupied cell " + p);
            restored.put(p,level.getBlockState(p)); require(level.setBlock(p,state,3), "Could not place real vanilla fixture block");
        }
        void start() {
            place(pos,Items.get("case1").block().defaultBlockState()); machineHost=(Case)level.getBlockEntity(pos);
            place(pos.east(4),Items.get("redstone").block().defaultBlockState());
            place(pos.east(5),Blocks.REDSTONE_BLOCK.defaultBlockState()); place(pos.south(2),Blocks.STONE.defaultBlockState());
            probe=new Probe(level); Network.joinNewNetwork(probe.node()); probe.node().connect(machineHost.machine().node());
            io=(Component)((Environment)level.getBlockEntity(pos.east(4))).node(); io.connect(machineHost.machine().node());
        }
        void verify() throws Exception {
            Object[] converted=((Component)probe.node()).invoke("data",machineHost.machine());
            require(converted.length==1 && converted[0] instanceof Map<?,?>,"Actual callback produced no payload");
            Map<?,?> payload=(Map<?,?>)converted[0];
            for (var dim : List.of(Level.OVERWORLD,Level.NETHER,Level.END)) {
                ServerLevel actual=level.getServer().getLevel(dim); require(actual!=null,"Actual server dimension absent");
                Object value=payload.get(dim.location().getPath());
                if (enabled) {
                    require(value instanceof Map<?,?>,"World did not flatten to public metadata"); Map<?,?> info=(Map<?,?>)value;
                    int legacy=dim.equals(Level.OVERWORLD)?0:dim.equals(Level.NETHER)?-1:1;
                    require(info.keySet().equals(Set.of("id","name")) && dim.location().getPath().equals(info.get("name")),"World leaked fields or changed its original dimension name");
                    require(originalId(actual.getSeed(),legacy).equals(info.get("id")),"World identity differs from original little-endian double-MD5");
                } else require(!(value instanceof Map<?,?>),"Blacklisted vanilla World converter still ran");
            }
            if (enabled) {
                Map<?,?> custom=(Map<?,?>)payload.get("custom"),other=(Map<?,?>)payload.get("other"),changed=(Map<?,?>)payload.get("changed");
                byte[] key="ocfixture:identity_a".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] bytes=ByteBuffer.allocate(8+key.length).order(ByteOrder.LITTLE_ENDIAN).putLong(level.getSeed()).put(key).array();
                String expected=UUID.nameUUIDFromBytes(MessageDigest.getInstance("MD5").digest(bytes)).toString();
                require(expected.equals(custom.get("id")) && "ocfixture:identity_a".equals(custom.get("name"))
                        && !custom.get("id").equals(other.get("id")) && !custom.get("id").equals(changed.get("id")),"Custom provider identity lost its stable dimension key or world seed");
                Map<?,?> filled=(Map<?,?>)payload.get("filled"),empty=(Map<?,?>)payload.get("empty"),tag=(Map<?,?>)payload.get("tag");
                require(((Number)filled.get("capacity")).intValue()==2000 && ((Number)filled.get("amount")).intValue()==750 && "minecraft:water".equals(filled.get("name")),"Actual tank contents/capacity conversion failed");
                require(((Number)empty.get("capacity")).intValue()==3000 && ((Number)empty.get("amount")).intValue()==0 && !empty.containsKey("name"),"Empty tank conversion invented fluid");
                require(((Number)tag.get("number")).intValue()==7 && payload.get("item") instanceof Map<?,?>,"Core NBT/item conversion missing");
            } else require(!(payload.get("filled") instanceof Map<?,?>) && !(payload.get("tag") instanceof Map<?,?>) && !(payload.get("item") instanceof Map<?,?>),"Blacklisted core vanilla converters still ran");
            var event=new GeolyzerEvent.Analyze(machineHost.machine().host(),Map.of(),pos.south(2)); NeoForge.EVENT_BUS.post(event);
            require(enabled ? net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","stone").equals(event.data.get("name")) : event.data.isEmpty(),"Real vanilla geolyzer event ignored its integration gate");
            int input=((Number)io.invoke("getInput",machineHost.machine(),Direction.EAST.get3DDataValue())[0]).intValue();
            require(input==(enabled?15:0),"Real redstone input provider ignored its integration gate: "+input);
            results.add("PASS I12.vanilla real Case-context callback flattens all three actual server worlds to original UUID/name without seed; tank/NBT/item converters, geolyzer event and actual redstone input gate enabled="+enabled);
            results.add("LIMIT I12: other vanilla drivers, recipe integration and custom-dimension/client-level gameplay remain separate acceptance; this does not close the whole I12 item");
        }
        void cleanup() {
            if(probe!=null)probe.node().remove(); if(machineHost!=null)machineHost.machine().stop();
            var entries=new ArrayList<>(restored.entrySet()); Collections.reverse(entries);
            for(var entry:entries){if(level.getBlockEntity(entry.getKey()) instanceof Container c)c.clearContent();level.setBlock(entry.getKey(),entry.getValue(),3);}
            for(long chunk:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk),net.minecraft.world.level.ChunkPos.getZ(chunk),false);
        }
    }
    public static final class Probe extends AbstractManagedEnvironment {
        final ServerLevel level; final FluidTank filled=new FluidTank(2000),empty=new FluidTank(3000);
        Probe(ServerLevel level) { this.level=level; filled.fill(new FluidStack(Fluids.WATER,750),FluidAction.EXECUTE); setNode(Network.newNode(this,Visibility.Network).withComponent("vanilla_probe").create()); }
        @Callback public Object[] data(Context context,Arguments args) throws Exception {
            Map<String,Object> values=new LinkedHashMap<>();
            for(var key:List.of(Level.OVERWORLD,Level.NETHER,Level.END))values.put(key.location().getPath(),level.getServer().getLevel(key));
            var constructor=Class.forName("li.cil.oc.server.driver.converter.WorldProvider$Snapshot").getConstructor(long.class,ResourceKey.class);
            var first=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("ocfixture:identity_a"));
            var second=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("ocfixture:identity_b"));
            values.put("custom",constructor.newInstance(level.getSeed(),first));
            values.put("other",constructor.newInstance(level.getSeed(),second));
            values.put("changed",constructor.newInstance(level.getSeed()+1,first));
            CompoundTag tag=new CompoundTag();tag.putInt("number",7); values.put("tag",tag); values.put("item",new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
            values.put("filled",filled);values.put("empty",empty);return new Object[]{values};
        }
    }
}
