package li.cil.oc.server.network;

import li.cil.oc.Settings;
import li.cil.oc.api.network.WirelessEndpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import java.util.*;

/** Per-dimension spatial trees with the original distance and sampled-hardness attenuation. */
public final class WirelessNetwork {
    private static final Map<ResourceKey<Level>, Index> DIMENSIONS = new HashMap<>();
    private WirelessNetwork() {}
    public static void add(WirelessEndpoint endpoint) {
        Level world = endpoint.world();
        if (world == null || world.isClientSide) return;
        Point point = new Point(endpoint, endpoint.x() + .5, endpoint.y() + .5, endpoint.z() + .5);
        synchronized (WirelessNetwork.class) { DIMENSIONS.computeIfAbsent(world.dimension(), ignored -> new Index()).add(point); }
    }
    public static void remove(WirelessEndpoint endpoint) { Level world = endpoint.world(); if (world != null) remove(endpoint, world.dimension()); }
    public static synchronized void remove(WirelessEndpoint endpoint, ResourceKey<Level> dimension) { Index index = DIMENSIONS.get(dimension); if (index != null) index.remove(endpoint); }
    public static void update(WirelessEndpoint endpoint) {
        Level world = endpoint.world();
        if (world == null) return;
        Point point = new Point(endpoint, endpoint.x() + .5, endpoint.y() + .5, endpoint.z() + .5);
        synchronized (WirelessNetwork.class) {
            Index index = DIMENSIONS.get(world.dimension());
            if (index != null) { Point previous = index.points.get(endpoint); if (previous != null && (Math.abs(previous.x - point.x) > .5 || Math.abs(previous.y - point.y) > .5 || Math.abs(previous.z - point.z) > .5)) index.add(point); }
        }
    }
    public static Collection<WirelessEndpoint> computeReachableFrom(WirelessEndpoint source, double strength) {
        Level world = source.world();
        if (strength <= 0 || world == null) return List.of();
        double radius = strength + 1;
        double x = source.x(), y = source.y(), z = source.z();
        Collection<WirelessEndpoint> candidates;
        synchronized (WirelessNetwork.class) {
            Index index = DIMENSIONS.get(world.dimension());
            if (index == null) return List.of();
            candidates = index.query(x + .5 - radius, y + .5 - radius, z + .5 - radius, x + .5 + radius, y + .5 + radius, z + .5 + radius);
        }
        // Endpoint callbacks and chunk lookups must never run while holding the spatial-index lock.
        List<WirelessEndpoint> result = new ArrayList<>();
        for (WirelessEndpoint endpoint : candidates) {
            if (endpoint == source || endpoint.world() != world) continue;
            double dx = endpoint.x() - x, dy = endpoint.y() - y, dz = endpoint.z() - z;
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared <= radius * radius && unobstructed(source, endpoint, Math.sqrt(squared), strength)) result.add(endpoint);
        }
        return result;
    }
    private static boolean unobstructed(WirelessEndpoint source, WirelessEndpoint target, double distance, double strength) {
        double gap = distance - 1;
        if (gap <= 0) return true;
        Level level = target.world();
        Vec3 origin = new Vec3(source.x(), source.y(), source.z());
        Vec3 direction = new Vec3(target.x(), target.y(), target.z()).subtract(origin).normalize();
        Vec3 up = direction.x == 0 && direction.z == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = direction.cross(up), top = direction.cross(side);
        int samples = Math.max(1, (int) Math.sqrt(gap)); double hardness = 0;
        for (int index = 0; index < samples; index++) {
            double step = level.random.nextDouble() * gap;
            int lateral = level.random.nextInt(3) - 1, vertical = level.random.nextInt(3) - 1;
            BlockPos pos = new BlockPos((int) (origin.x + direction.x * step + side.x * lateral + top.x * vertical), (int) (origin.y + direction.y * step + side.y * lateral + top.y * vertical), (int) (origin.z + direction.z * step + side.z * lateral + top.z * vertical));
            if (level.hasChunkAt(pos)) hardness += level.getBlockState(pos).getDestroySpeed(level, pos);
        }
        return strength - gap > hardness * gap / samples;
    }
    @SubscribeEvent public static synchronized void onLevelLoad(LevelEvent.Load event) { if (event.getLevel() instanceof Level level && !level.isClientSide) DIMENSIONS.remove(level.dimension()); }
    @SubscribeEvent public static synchronized void onLevelUnload(LevelEvent.Unload event) { if (event.getLevel() instanceof Level level && !level.isClientSide) DIMENSIONS.remove(level.dimension()); }
    @SubscribeEvent public static void onChunkUnload(ChunkEvent.Unload event) { if (event.getChunk() instanceof LevelChunk chunk) for (var entity : chunk.getBlockEntities().values()) if (entity instanceof WirelessEndpoint endpoint) remove(endpoint); }
    /** Cloned numeric bounds only; no endpoint, mutable branch, or world reference escapes. */
    public record DebugBound(double minX,double minY,double minZ,double maxX,double maxY,double maxZ,int depth){}
    public static synchronized Map<ResourceKey<Level>,List<DebugBound>> debugBounds(){
        Map<ResourceKey<Level>,List<DebugBound>> result=new HashMap<>();
        DIMENSIONS.forEach((dimension,index)->{List<DebugBound> bounds=new ArrayList<>();index.root.bounds(0,bounds);if(!bounds.isEmpty())result.put(dimension,List.copyOf(bounds));});
        return Map.copyOf(result);
    }
    private record Point(WirelessEndpoint endpoint, double x, double y, double z) {}
    /** Bounding-volume tree; median splits keep broad-phase queries spatially bounded. */
    private static final class Index {
        final Map<WirelessEndpoint, Point> points = new IdentityHashMap<>();
        Branch root = new Branch();
        void add(Point point) { remove(point.endpoint); points.put(point.endpoint, point); root.insert(point); }
        void remove(WirelessEndpoint endpoint) { Point point = points.remove(endpoint); if (point != null) root.remove(point); }
        Collection<WirelessEndpoint> query(double ax, double ay, double az, double bx, double by, double bz) { List<WirelessEndpoint> result = new ArrayList<>(); root.query(ax, ay, az, bx, by, bz, result); return result; }
        static final class Branch {
            final List<Point> leaves = new ArrayList<>(); Branch left, right;
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            void include(Point p) { minX = Math.min(minX,p.x); minY = Math.min(minY,p.y); minZ = Math.min(minZ,p.z); maxX = Math.max(maxX,p.x); maxY = Math.max(maxY,p.y); maxZ = Math.max(maxZ,p.z); }
            double enlargement(Point p) { if(minX==Double.POSITIVE_INFINITY)return 0; return Math.max(maxX,p.x)-Math.min(minX,p.x)+Math.max(maxY,p.y)-Math.min(minY,p.y)+Math.max(maxZ,p.z)-Math.min(minZ,p.z)-(maxX-minX+maxY-minY+maxZ-minZ); }
            void insert(Point p) {
                include(p);
                if (left != null) { (left.enlargement(p) <= right.enlargement(p) ? left : right).insert(p); return; }
                leaves.add(p);
                if (leaves.size() > Math.max(4,Settings.get().rTreeMaxEntries)) {
                    int axis = maxX-minX >= maxY-minY && maxX-minX >= maxZ-minZ ? 0 : maxY-minY >= maxZ-minZ ? 1 : 2;
                    leaves.sort(Comparator.comparingDouble(v -> axis == 0 ? v.x : axis == 1 ? v.y : v.z));
                    left = new Branch(); right = new Branch();
                    for (int i=0;i<leaves.size();i++) (i<leaves.size()/2?left:right).insert(leaves.get(i));
                    leaves.clear();
                }
            }
            void remove(Point p) {
                if (left == null) leaves.remove(p); else { left.remove(p); right.remove(p); }
                minX=minY=minZ=Double.POSITIVE_INFINITY; maxX=maxY=maxZ=Double.NEGATIVE_INFINITY;
                if (left == null) for(Point point:leaves) include(point); else { includeBounds(left); includeBounds(right); }
            }
            void includeBounds(Branch b) { minX=Math.min(minX,b.minX);minY=Math.min(minY,b.minY);minZ=Math.min(minZ,b.minZ);maxX=Math.max(maxX,b.maxX);maxY=Math.max(maxY,b.maxY);maxZ=Math.max(maxZ,b.maxZ); }
            void query(double ax,double ay,double az,double bx,double by,double bz,List<WirelessEndpoint> result) {
                if (maxX<ax||maxY<ay||maxZ<az||minX>bx||minY>by||minZ>bz) return;
                if(left!=null){left.query(ax,ay,az,bx,by,bz,result);right.query(ax,ay,az,bx,by,bz,result);}
                else for(Point p:leaves)if(p.x>=ax&&p.y>=ay&&p.z>=az&&p.x<=bx&&p.y<=by&&p.z<=bz)result.add(p.endpoint);
            }
            void bounds(int depth,List<DebugBound> result){
                if(!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)||!Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ))return;
                result.add(new DebugBound(minX,minY,minZ,maxX,maxY,maxZ,depth));
                if(left!=null){left.bounds(depth+1,result);right.bounds(depth+1,result);}
                else for(Point point:leaves)result.add(new DebugBound(point.x,point.y,point.z,point.x,point.y,point.z,depth+1));
            }
        }
    }
}
