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
    public static void add(WirelessEndpoint endpoint) { if (endpoint.world() != null && !endpoint.world().isClientSide) DIMENSIONS.computeIfAbsent(endpoint.world().dimension(), ignored -> new Index()).add(endpoint); }
    public static void remove(WirelessEndpoint endpoint) { if (endpoint.world() != null) remove(endpoint, endpoint.world().dimension()); }
    public static void remove(WirelessEndpoint endpoint, ResourceKey<Level> dimension) { Index index = DIMENSIONS.get(dimension); if (index != null) index.remove(endpoint); }
    public static void update(WirelessEndpoint endpoint) {
        if (endpoint.world() == null) return;
        Index index = DIMENSIONS.get(endpoint.world().dimension());
        if (index != null) { Point previous = index.points.get(endpoint); if (previous != null && (Math.abs(previous.x - endpoint.x() - .5) > .5 || Math.abs(previous.y - endpoint.y() - .5) > .5 || Math.abs(previous.z - endpoint.z() - .5) > .5)) { index.remove(endpoint); index.add(endpoint); } }
    }
    public static Collection<WirelessEndpoint> computeReachableFrom(WirelessEndpoint source, double strength) {
        if (strength <= 0 || source.world() == null) return List.of();
        Index index = DIMENSIONS.get(source.world().dimension());
        if (index == null) return List.of();
        double radius = strength + 1;
        List<WirelessEndpoint> result = new ArrayList<>();
        for (WirelessEndpoint endpoint : index.query(source.x() + .5 - radius, source.y() + .5 - radius, source.z() + .5 - radius, source.x() + .5 + radius, source.y() + .5 + radius, source.z() + .5 + radius)) {
            if (endpoint == source) continue;
            double dx = endpoint.x() - source.x(), dy = endpoint.y() - source.y(), dz = endpoint.z() - source.z();
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
    @SubscribeEvent public static void onLevelLoad(LevelEvent.Load event) { if (event.getLevel() instanceof Level level && !level.isClientSide) DIMENSIONS.remove(level.dimension()); }
    @SubscribeEvent public static void onLevelUnload(LevelEvent.Unload event) { if (event.getLevel() instanceof Level level && !level.isClientSide) DIMENSIONS.remove(level.dimension()); }
    @SubscribeEvent public static void onChunkUnload(ChunkEvent.Unload event) { if (event.getChunk() instanceof LevelChunk chunk) for (var entity : chunk.getBlockEntities().values()) if (entity instanceof WirelessEndpoint endpoint) remove(endpoint); }
    private record Point(WirelessEndpoint endpoint, double x, double y, double z) {}
    /** Bounding-volume tree; median splits keep broad-phase queries spatially bounded. */
    private static final class Index {
        final Map<WirelessEndpoint, Point> points = new IdentityHashMap<>();
        Branch root = new Branch();
        void add(WirelessEndpoint endpoint) { remove(endpoint); Point point = new Point(endpoint, endpoint.x() + .5, endpoint.y() + .5, endpoint.z() + .5); points.put(endpoint, point); root.insert(point); }
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
        }
    }
}
