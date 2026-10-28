package li.cil.oc.common.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.client.ScreenClient;
import li.cil.oc.client.renderer.ScreenTextRenderer;
import li.cil.oc.common.block.ScreenBlock;
import li.cil.oc.common.component.Screen;
import li.cil.oc.common.component.TextBuffer;
import li.cil.oc.common.network.ScreenNetworking;
import li.cil.oc.server.component.Keyboard;
import li.cil.oc.util.SideTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Persistent screen environment and the original deterministic rectangular grouping. */
public class ScreenBlockEntity extends BlockEntity implements Screen.Owner, Environment, SidedEnvironment, li.cil.oc.api.network.Analyzable, li.cil.oc.api.internal.Colored, li.cil.oc.api.internal.Rotatable {
    public final int tier;
    private Screen buffer;
    private BlockPos origin;
    private final Set<ScreenBlockEntity> members = new LinkedHashSet<>();
    private int groupWidth = 1, groupHeight = 1, color = 0xFFFFFF;
    private boolean groupDirty = true, hadRedstone, redstoneInitialized;
    private volatile boolean invertedTouch;
    private long revision;
    private final AtomicBoolean pendingSave = new AtomicBoolean();
    private final AtomicBoolean pendingSnapshot = new AtomicBoolean();
    private final ConcurrentLinkedQueue<List<TextBuffer.Command>> queuedCommands = new ConcurrentLinkedQueue<>();
    private final Map<String, Map<Integer, CompoundTag>> publishedVideoPages = new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<String> invalidatedVideoOwners = new ConcurrentLinkedQueue<>();
    private final WeakHashMap<Entity, BlockPos> lastWalked = new WeakHashMap<>();

    public ScreenBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int tier) {
        super(type, pos, state); this.tier = tier; origin = pos; members.add(this);
        color = new net.minecraft.world.item.DyeColor[]{net.minecraft.world.item.DyeColor.LIGHT_GRAY,net.minecraft.world.item.DyeColor.YELLOW,net.minecraft.world.item.DyeColor.CYAN}[Math.clamp(tier,0,2)].getTextureDiffuseColor() & 0xFFFFFF;
    }
    public synchronized Screen buffer() {
        if (buffer == null) {
            buffer = new Screen(this, new Services()) {
                @Override public boolean addBuffer(li.cil.oc.common.component.GpuTextBuffer ram) {
                    // GPU bitblt already holds this buffer's normal execution lock.
                    // Remove detached references here, never from the server tick.
                    String owner;
                    while ((owner = invalidatedVideoOwners.poll()) != null) removeAllBuffers(owner);
                    return super.addBuffer(ram);
                }
            };
            int[] size = Settings.screenResolutionsByTier[Math.clamp(tier, 0, Settings.screenResolutionsByTier.length - 1)];
            buffer.setMaximumResolution(size[0], size[1]);
            buffer.setMaximumColorDepth(Settings.screenDepthsByTier[Math.clamp(tier, 0, Settings.screenDepthsByTier.length - 1)]);
        }
        return buffer;
    }
    @Override public Level world() { return level; }
    @Override public double xPosition() { return worldPosition.getX() + 0.5; }
    @Override public double yPosition() { return worldPosition.getY() + 0.5; }
    @Override public double zPosition() { return worldPosition.getZ() + 0.5; }
    @Override public void markChanged() { setChanged(); }
    @Override public void setChanged() {
        if (level instanceof ServerLevel server && !server.getServer().isSameThread()) { pendingSave.set(true); return; }
        pendingSave.getAndSet(false);
        super.setChanged();
    }
    private boolean onServerThread() { return level instanceof ServerLevel server && server.getServer().isSameThread(); }
    @Override public Node node() { return buffer().node(); }
    @Override public Node[] onAnalyze(Player player, Direction side, float hitX, float hitY, float hitZ) { return new Node[]{origin().node()}; }
    @Override public void onConnect(Node node) { buffer().onConnect(node); }
    @Override public void onDisconnect(Node node) { buffer().onDisconnect(node); }
    @Override public void onMessage(Message message) { buffer().onMessage(message); }
    @Override public Node sidedNode(Direction side) {
        if (side != facing()) return node();
        return level != null && level.hasChunkAt(worldPosition.relative(side)) && level.getBlockEntity(worldPosition.relative(side)) instanceof KeyboardBlockEntity ? node() : null;
    }
    @OnlyIn(Dist.CLIENT) @Override public boolean canConnect(Direction side) { return side != facing(); }
    public Direction facing() { return ScreenBlock.facing(getBlockState()); }
    @Override public Direction toGlobal(Direction side) { return ScreenBlock.toGlobal(getBlockState(), side); }
    @Override public Direction toLocal(Direction side) { return ScreenBlock.toLocal(getBlockState(), side); }
    public Direction up() { return facing() == Direction.UP ? getBlockState().getValue(ScreenBlock.YAW).getOpposite() : facing() == Direction.DOWN ? getBlockState().getValue(ScreenBlock.YAW) : Direction.UP; }
    public Direction right() {
        Direction up = up(), front = facing();
        return Objects.requireNonNull(Direction.fromDelta(up.getStepY() * front.getStepZ() - up.getStepZ() * front.getStepY(),
                up.getStepZ() * front.getStepX() - up.getStepX() * front.getStepZ(), up.getStepX() * front.getStepY() - up.getStepY() * front.getStepX()));
    }
    public boolean isOrigin() { return origin.equals(worldPosition); }
    public ScreenBlockEntity origin() {
        if (isOrigin() || level == null || !level.hasChunkAt(origin)) return this;
        return level.getBlockEntity(origin) instanceof ScreenBlockEntity screen ? screen : this;
    }
    public int groupWidth() { return groupWidth; }
    public int groupHeight() { return groupHeight; }
    @OnlyIn(Dist.CLIENT) public li.cil.oc.client.renderer.ScreenBakedModel.Geometry geometry() {
        return new li.cil.oc.client.renderer.ScreenBakedModel.Geometry(groupWidth, groupHeight,
                project(worldPosition, right()) - project(origin, right()), project(worldPosition, up()) - project(origin, up()),
                getBlockState().getValue(ScreenBlock.PITCH), getBlockState().getValue(ScreenBlock.YAW));
    }
    @OnlyIn(Dist.CLIENT) @Override public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
        return net.neoforged.neoforge.client.model.data.ModelData.of(li.cil.oc.client.renderer.ScreenBakedModel.GEOMETRY, geometry());
    }
    public long revision() { return revision; }
    public int color() { return color; }
    @Override public int getColor() { return color; }
    @Override public boolean controlsConnectivity() { return false; }
    public void setColor(int value) { int next=value&0xFFFFFF;if(color==next)return;color=next;resetGroup();setChanged(); }
    @Override public boolean invertTouchMode() { return invertedTouch; }
    @Override public void setInvertTouchMode(boolean value) { invertedTouch = value; setChanged(); }
    @Override public void synchronizeTouchMode(boolean value) { publishSnapshot(); }
    public void invalidateGroup() { for (ScreenBlockEntity member : List.copyOf(members)) member.groupDirty = true; groupDirty = true; }
    private void resetGroup() { resetGroup(false); }
    private void resetGroup(boolean disposing) {
        for(ScreenBlockEntity member:List.copyOf(members)){
            member.groupDirty=true;member.invertedTouch=false;
            if((!disposing||member!=this)&&member.level!=null&&member.level.hasChunkAt(member.worldPosition)&&!member.isRemoved())member.setChanged();
        }
        groupDirty=true;
    }
    @Override public void setBlockState(BlockState state) {
        BlockState previous=getBlockState();
        boolean rotated=previous.hasProperty(ScreenBlock.PITCH)&&state.hasProperty(ScreenBlock.PITCH)
                &&(previous.getValue(ScreenBlock.PITCH)!=state.getValue(ScreenBlock.PITCH)||previous.getValue(ScreenBlock.YAW)!=state.getValue(ScreenBlock.YAW));
        super.setBlockState(state);
        if(rotated){
            resetGroup();
            if(level!=null&&!level.isClientSide&&buffer!=null){
                for(Direction side:Direction.values())if(sidedNode(side)==null){
                    BlockPos neighbor=worldPosition.relative(side);
                    if(level.hasChunkAt(neighbor)){Node remote=li.cil.oc.server.network.Network.getNetworkNode(level.getBlockEntity(neighbor),side.getOpposite());if(remote!=null)node().disconnect(remote);}
                }
                level.invalidateCapabilities(worldPosition);Network.joinOrCreateNetwork(this);
            }
        }
    }
    private int project(BlockPos pos, Direction axis) { return pos.getX() * axis.getStepX() + pos.getY() * axis.getStepY() + pos.getZ() * axis.getStepZ(); }
    private boolean samePlane(ScreenBlockEntity other) {
        return other.getBlockState().getValue(ScreenBlock.PITCH) == getBlockState().getValue(ScreenBlock.PITCH) &&
                other.getBlockState().getValue(ScreenBlock.YAW) == getBlockState().getValue(ScreenBlock.YAW);
    }
    private void rebuildGroup() {
        if (level == null || level.isClientSide) return;
        Comparator<ScreenBlockEntity> order = Comparator.comparingInt((ScreenBlockEntity s) -> s.worldPosition.getX()).thenComparingInt(s -> s.worldPosition.getY()).thenComparingInt(s -> s.worldPosition.getZ());
        TreeSet<ScreenBlockEntity> pending = new TreeSet<>(order);
        ArrayDeque<ScreenBlockEntity> queue = new ArrayDeque<>(); pending.add(this); queue.add(this);
        while (!queue.isEmpty()) {
            ScreenBlockEntity current = queue.remove();
            for (Direction axis : new Direction[]{right(), right().getOpposite(), up(), up().getOpposite()}) {
                BlockPos neighbor = current.worldPosition.relative(axis);
                if (level.hasChunkAt(neighbor) && level.getBlockEntity(neighbor) instanceof ScreenBlockEntity screen && samePlane(screen) && pending.add(screen)) queue.add(screen);
            }
        }
        List<ScreenBlockEntity> all = List.copyOf(pending);
        for (ScreenBlockEntity screen : all) { screen.origin = screen.worldPosition; screen.groupWidth = screen.groupHeight = 1; screen.members.clear(); screen.members.add(screen); }
        while (!pending.isEmpty()) {
            ScreenBlockEntity current = pending.first();
            while (current.tryMerge()) { }
            pending.removeAll(current.members);
        }
        for (ScreenBlockEntity screen : all) {
            screen.groupDirty = false;
            if (screen.node() instanceof li.cil.oc.api.network.Component component) component.setVisibility(screen.isOrigin() ? Visibility.Network : Visibility.None);
            if (screen.isOrigin()) { screen.buffer().setEnergyCostPerTick(Settings.get().screenCost * screen.groupWidth * screen.groupHeight); screen.buffer().setAspectRatio(screen.groupWidth, screen.groupHeight); }
            else {
                screen.buffer().setEnergyCostPerTick(Settings.get().screenCost); screen.buffer().setAspectRatio(1, 1);
                screen.buffer().setForegroundColor(0xFFFFFF); screen.buffer().setBackgroundColor(0); screen.buffer().fill(0, 0, screen.buffer().getWidth(), screen.buffer().getHeight(), ' ');
            }
            screen.publishSnapshot();
        }
    }
    private boolean tryMerge() {
        ScreenBlockEntity base = origin();
        BlockPos[] candidates = {base.worldPosition.relative(up(), groupHeight), base.worldPosition.relative(up().getOpposite()),
                base.worldPosition.relative(right(), groupWidth), base.worldPosition.relative(right().getOpposite())};
        for (BlockPos candidate : candidates) {
            if (!level.hasChunkAt(candidate) || !(level.getBlockEntity(candidate) instanceof ScreenBlockEntity other) || !samePlane(other) || other.tier != tier || other.color != color || members.contains(other)) continue;
            ScreenBlockEntity otherOrigin = other.origin();
            int u = project(base.worldPosition, right()), v = project(base.worldPosition, up()), ou = project(otherOrigin.worldPosition, right()), ov = project(otherOrigin.worldPosition, up());
            boolean alongX = ov == v && other.groupHeight == groupHeight && other.groupWidth + groupWidth <= Settings.get().maxScreenWidth;
            boolean alongY = ou == u && other.groupWidth == groupWidth && other.groupHeight + groupHeight <= Settings.get().maxScreenHeight;
            if (!alongX && !alongY) continue;
            ScreenBlockEntity nextOrigin = alongX ? ou < u ? otherOrigin : base : ov < v ? otherOrigin : base;
            int width = alongX ? groupWidth + other.groupWidth : groupWidth, height = alongX ? groupHeight : groupHeight + other.groupHeight;
            LinkedHashSet<ScreenBlockEntity> joined = new LinkedHashSet<>(members); joined.addAll(other.members);
            for (ScreenBlockEntity screen : joined) { screen.origin = nextOrigin.worldPosition; screen.groupWidth = width; screen.groupHeight = height; screen.members.clear(); screen.members.addAll(joined); }
            return true;
        }
        return false;
    }
    public Collection<Node> keyboardNodes() {
        LinkedHashSet<Node> result = new LinkedHashSet<>();
        for (ScreenBlockEntity member : origin().members) if (member.node() != null && member.node().network() != null)
            for (Node neighbor : member.node().neighbors()) if (neighbor.host() instanceof li.cil.oc.api.internal.Keyboard) result.add(neighbor);
        return result;
    }
    public boolean hasKeyboard() {
        if(level==null)return false;
        for(ScreenBlockEntity member:origin().members)for(Direction side:Direction.values()){
            BlockPos neighbor=member.worldPosition.relative(side);
            if(level.hasChunkAt(neighbor)&&level.getBlockEntity(neighbor) instanceof KeyboardBlockEntity keyboard&&keyboard.hasNodeOnSide(side.getOpposite()))return true;
        }
        return false;
    }
    public void releaseKeys(Player player) { for (Node keyboard : keyboardNodes()) if (keyboard.host() instanceof Keyboard component) component.releasePressedKeys(player); }
    private double[] faceCoordinates(Vec3 point) {
        ScreenBlockEntity screen = origin();
        Vec3 relative = point.subtract(Vec3.atCenterOf(screen.worldPosition));
        double x = relative.dot(Vec3.atLowerCornerOf(right().getNormal())) + 0.5;
        double y = screen.groupHeight - 0.5 - relative.dot(Vec3.atLowerCornerOf(up().getNormal()));
        return new double[]{x,y};
    }
    public boolean withinDisplayBorder(Vec3 point) {
        double[] face=faceCoordinates(point);ScreenBlockEntity screen=origin();double border=2.25/16;
        return face[0]>border&&face[1]>border&&face[0]<screen.groupWidth-border&&face[1]<screen.groupHeight-border;
    }
    @OnlyIn(Dist.CLIENT) public double[] toScreenCoordinates(Vec3 point) {
        if(!withinDisplayBorder(point))return null;
        ScreenBlockEntity screen=origin();double[] face=faceCoordinates(point);double border=2.25/16;
        double availableWidth=screen.groupWidth-2*border,availableHeight=screen.groupHeight-2*border;
        double rx=(face[0]-border)/availableWidth,ry=(face[1]-border)/availableHeight;
        double pixelWidth=screen.buffer().renderWidth()/availableWidth,pixelHeight=screen.buffer().renderHeight()/availableHeight;
        if(pixelWidth>pixelHeight){double ratio=pixelHeight/pixelWidth;ry=(ry-(1-ratio)*.5)/ratio;}
        else if(pixelHeight>pixelWidth){double ratio=pixelWidth/pixelHeight;rx=(rx-(1-ratio)*.5)/ratio;}
        if(rx<0||ry<0||rx>=1||ry>=1)return null;
        return new double[]{rx*screen.buffer().getViewportWidth(),ry*screen.buffer().getViewportHeight()};
    }
    public void walk(Entity entity) {
        ScreenBlockEntity screen = origin();
        int x = project(worldPosition, right()) - project(screen.worldPosition, right());
        int y = project(worldPosition, up()) - project(screen.worldPosition, up());
        BlockPos cell=new BlockPos(x,y,0);
        if(cell.equals(screen.lastWalked.put(entity,cell)))return;
        if (screen.node() != null) {
            if (Settings.get().inputUsername && entity instanceof Player player) screen.node().sendToReachable("computer.signal", "walk", x + 1, screen.groupHeight - y, player.getGameProfile().getName());
            else screen.node().sendToReachable("computer.signal", "walk", x + 1, screen.groupHeight - y);
        }
    }
    public void tick() {
        if (level == null) return;
        if (!level.isClientSide) {
            if (pendingSave.getAndSet(false)) super.setChanged();
            drainQueuedCommands();
            discardUnlinkedVideoPages();
        }
        if (!level.isClientSide && groupDirty) rebuildGroup();
        if (!level.isClientSide && isOrigin()) {
            boolean redstone = members.stream().anyMatch(member -> level.hasNeighborSignal(member.worldPosition));
            if(redstoneInitialized&&redstone&&!hadRedstone)buffer().setPowerState(!buffer().getPowerState());
            for(ScreenBlockEntity member:members){
                if(member.hadRedstone!=redstone)member.setChanged();
                member.hadRedstone=redstone;member.redstoneInitialized=true;
            }
        }
        buffer().update();
        if (!level.isClientSide && pendingSnapshot.getAndSet(false)) publishSnapshotNow();
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) Network.joinOrCreateNetwork(this);
    }
    @Override public void onChunkUnloaded() { if (node() != null) node().remove(); clearVideoPages(); queuedCommands.clear(); resetGroup(true); super.onChunkUnloaded(); }
    @Override public void setRemoved() { if (node() != null) node().remove(); clearVideoPages(); queuedCommands.clear(); resetGroup(true); super.setRemoved(); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("screenSync")) { applySnapshot(tag.getCompound("screenSync"), tag.getLong("revision")); return; }
        invalidatedVideoOwners.addAll(publishedVideoPages.keySet()); publishedVideoPages.clear();
        buffer().load(tag.contains("oc:screen")?tag.getCompound("oc:screen"):tag, registries);
        if(tag.contains("oc:renderColor"))color=net.minecraft.world.item.DyeColor.byId(tag.getInt("oc:renderColor")).getTextureDiffuseColor();
        if(tag.contains("oc:renderColorRGB"))color=tag.getInt("oc:renderColorRGB");
        if(tag.contains("oc:color"))color=tag.getInt("oc:color");
        color&=0xFFFFFF;
        hadRedstone=tag.getBoolean("oc:hadRedstoneInput");redstoneInitialized=tag.contains("oc:hadRedstoneInput");
        invertedTouch = tag.getBoolean("oc:invertTouchMode"); groupDirty = true;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); CompoundTag screen = new CompoundTag(); buffer().save(screen, registries);
        tag.put("oc:screen", screen); tag.putInt("oc:color", color); tag.putBoolean("oc:invertTouchMode", invertedTouch);
        tag.putByte("oc:tier",(byte)tier);tag.putInt("oc:renderColorRGB",color);tag.putBoolean("oc:hadRedstoneInput",hadRedstone);
    }
    public CompoundTag snapshot() {
        if (onServerThread()) discardUnlinkedVideoPages();
        CompoundTag tag = new CompoundTag(); buffer().data().save(tag);
        ListTag pages = new ListTag();
        for (Map<Integer, CompoundTag> device : publishedVideoPages.values()) for (CompoundTag page : device.values()) pages.add(page.copy());
        tag.put("vram", pages);
        tag.putInt("maxWidth", buffer().getMaximumWidth()); tag.putInt("maxHeight", buffer().getMaximumHeight());
        tag.putLong("origin", origin.asLong()); tag.putInt("physicalWidth", groupWidth); tag.putInt("physicalHeight", groupHeight);
        tag.putBoolean("on", buffer().getPowerState()); tag.putBoolean("render", buffer().getPowerState() && buffer().hasPower());
        tag.putInt("viewportWidth", buffer().getViewportWidth()); tag.putInt("viewportHeight", buffer().getViewportHeight());
        tag.putBoolean("precise",(Boolean)buffer().isPrecise(null,null)[0]);
        tag.putString("address", buffer().nodeAddress() == null ? "" : buffer().nodeAddress()); tag.putInt("color", color); tag.putBoolean("inverted", invertedTouch);
        tag.putBoolean("keyboard", hasKeyboard()); return tag;
    }
    public boolean clientHasKeyboard;
    public void applySnapshot(CompoundTag tag, long sequence) {
        BlockPos previousOrigin = origin;
        int previousWidth = groupWidth, previousHeight = groupHeight, previousColor = color;
        if (tag.contains("maxWidth", Tag.TAG_INT) && tag.contains("maxHeight", Tag.TAG_INT)) buffer().setMaximumResolution(tag.getInt("maxWidth"), tag.getInt("maxHeight"));
        buffer().data().load(tag); origin = BlockPos.of(tag.getLong("origin"));
        buffer().videoDevices().clear();
        for (Tag raw : tag.getList("vram", Tag.TAG_COMPOUND)) {
            CompoundTag page = (CompoundTag) raw;
            buffer().loadBuffer(page.getString("owner"), page.getInt("id"), page);
        }
        groupWidth = Math.max(1, tag.getInt("physicalWidth")); groupHeight = Math.max(1, tag.getInt("physicalHeight"));
        buffer().setViewport(Math.clamp(tag.getInt("viewportWidth"), 1, buffer().getWidth()), Math.clamp(tag.getInt("viewportHeight"), 1, buffer().getHeight()));
        buffer().setPowerState(tag.getBoolean("on")); buffer().setRenderingEnabled(tag.getBoolean("render"));
        buffer().applyPrecisionMode(tag.getBoolean("precise"));
        color = tag.getInt("color") & 0xFFFFFF; invertedTouch = tag.getBoolean("inverted"); clientHasKeyboard = tag.getBoolean("keyboard");
        revision = sequence; buffer().markInitialized(); buffer().dirty = true;
        if (level != null && level.isClientSide && (!origin.equals(previousOrigin) || groupWidth != previousWidth || groupHeight != previousHeight || color != previousColor)) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }
    @OnlyIn(Dist.CLIENT) public void applyCommands(CompoundTag tag, long sequence) {
        if (sequence <= revision) return;
        if (sequence != revision + 1) { ScreenClient.request(this); return; }
        for (Tag value : tag.getList("commands", Tag.TAG_COMPOUND)) {
            CompoundTag command = (CompoundTag) value; Object[] p = ScreenNetworking.commandValues(command);
            switch (command.getString("type")) {
                case "power" -> buffer().applyPowerState((Boolean) p[0]);
                case "precise" -> buffer().applyPrecisionMode((Boolean)p[0]);
                case "maxResolution" -> buffer().setMaximumResolution((Integer) p[0], (Integer) p[1]);
                case "resolution" -> buffer().setResolution((Integer) p[0], (Integer) p[1]);
                case "viewport" -> buffer().setViewport((Integer) p[0], (Integer) p[1]);
                case "depth" -> buffer().setColorDepth((li.cil.oc.api.internal.TextBuffer.ColorDepth) p[0]);
                case "palette" -> buffer().setPaletteColor((Integer) p[0], (Integer) p[1]);
                case "color" -> { var fg = (li.cil.oc.util.PackedColor.Color) p[0]; var bg = (li.cil.oc.util.PackedColor.Color) p[1]; buffer().setForegroundColor(fg.value(), fg.isPalette()); buffer().setBackgroundColor(bg.value(), bg.isPalette()); }
                case "copy" -> buffer().copy((Integer) p[0], (Integer) p[1], (Integer) p[2], (Integer) p[3], (Integer) p[4], (Integer) p[5]);
                case "fill" -> buffer().fill((Integer) p[0], (Integer) p[1], (Integer) p[2], (Integer) p[3], (Integer) p[4]);
                case "set" -> buffer().set((Integer) p[0], (Integer) p[1], (String) p[2], (Boolean) p[3]);
                case "ramInit" -> buffer().loadBuffer((String) p[0], (Integer) p[1], (CompoundTag) p[2]);
                case "ramDestroy" -> buffer().removeBuffer((String) p[0], (Integer) p[1]);
                case "bitblt" -> li.cil.oc.common.component.ClientGpuTextBufferHandler.bitblt(buffer(), (Integer) p[0], (Integer) p[1], (Integer) p[2], (Integer) p[3], (String) p[4], (Integer) p[5], (Integer) p[6], (Integer) p[7]);
                case "rawText" -> buffer().rawSetText((Integer) p[0], (Integer) p[1], (int[][]) p[2]);
                case "rawForeground" -> buffer().rawSetForeground((Integer) p[0], (Integer) p[1], (int[][]) p[2]);
                case "rawBackground" -> buffer().rawSetBackground((Integer) p[0], (Integer) p[1], (int[][]) p[2]);
                default -> throw new IllegalArgumentException("Unknown screen update");
            }
        }
        revision = sequence;
    }
    public void flushPendingCommands() {
        if (level != null && !level.isClientSide) {
            if (onServerThread()) drainQueuedCommands();
            buffer().flushCommands();
        }
    }
    private void publishCommands(List<TextBuffer.Command> commands) {
        if (!onServerThread()) { queuedCommands.add(List.copyOf(commands)); return; }
        drainQueuedCommands();
        publishCommandsNow(commands);
    }
    private void publishCommandsNow(List<TextBuffer.Command> commands) {
        for (TextBuffer.Command command : commands) {
            Object[] values = command.payload();
            if (command.type().equals("ramInit")) {
                CompoundTag page = ((CompoundTag) values[2]).copy();
                page.putString("owner", (String) values[0]); page.putInt("id", (Integer) values[1]);
                publishedVideoPages.computeIfAbsent((String) values[0], owner -> new LinkedHashMap<>()).put((Integer) values[1], page);
            } else if (command.type().equals("ramDestroy")) {
                Map<Integer, CompoundTag> device = publishedVideoPages.get((String) values[0]);
                if (device != null) { device.remove((Integer) values[1]); if (device.isEmpty()) publishedVideoPages.remove((String) values[0]); }
            }
        }
        revision++; ScreenNetworking.broadcast(this, false, ScreenNetworking.commands(commands));
    }
    private void discardUnlinkedVideoPages() {
        if (publishedVideoPages.isEmpty()) return;
        var screenNode = buffer == null ? null : buffer.node();
        var network = screenNode == null ? null : screenNode.network();
        publishedVideoPages.keySet().removeIf(owner -> {
            boolean unlinked = network == null || network.node(owner) == null;
            if (unlinked) invalidatedVideoOwners.add(owner);
            return unlinked;
        });
    }
    private void clearVideoPages() {
        publishedVideoPages.clear();
        invalidatedVideoOwners.clear();
        if (buffer != null) buffer.videoDevices().clear();
    }
    private void drainQueuedCommands() {
        List<TextBuffer.Command> commands;
        while ((commands = queuedCommands.poll()) != null) publishCommandsNow(commands);
    }
    public void publishSnapshot() {
        if (level != null && level.isClientSide) return;
        if (!onServerThread()) { pendingSnapshot.set(true); return; }
        pendingSnapshot.getAndSet(false);
        publishSnapshotNow();
    }
    private void publishSnapshotNow() {
        flushPendingCommands(); revision++;
        ScreenNetworking.broadcast(this, true, snapshot());
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { flushPendingCommands(); CompoundTag tag = new CompoundTag(); tag.put("screenSync", snapshot()); tag.putLong("revision", revision); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    public AABB screenBounds() {
        BlockPos opposite = worldPosition.relative(right(), groupWidth - 1).relative(up(), groupHeight - 1);
        return new AABB(worldPosition).minmax(new AABB(opposite)).inflate(0.1);
    }
    private final class Services implements TextBuffer.HostServices {
        @Override public boolean client() { return level != null ? level.isClientSide : SideTracker.isClient(); }
        @Override public void sendCommands(li.cil.oc.api.network.EnvironmentHost host, String address, List<TextBuffer.Command> commands) { publishCommands(commands); }
        @Override public void requestInitialization(String address) { if (client() && level != null) ScreenClient.request(ScreenBlockEntity.this); }
        @Override public void register(li.cil.oc.api.network.EnvironmentHost host, String address, TextBuffer buffer) { setChanged(); }
        @Override public void unregister(li.cil.oc.api.network.EnvironmentHost host, TextBuffer buffer) { if (client()) ScreenClient.forget(ScreenBlockEntity.this); }
        @Override public void persistBuffer(li.cil.oc.api.network.EnvironmentHost host, CompoundTag metadata, String path, Consumer<CompoundTag> writer) { CompoundTag data = new CompoundTag(); writer.accept(data); metadata.put("buffer", data); }
        @Override public CompoundTag loadBuffer(CompoundTag metadata, String path) { return metadata.getCompound("buffer"); }
        @Override public Collection<Node> keyboardNodes(TextBuffer buffer) { return ScreenBlockEntity.this.keyboardNodes(); }
        @Override public void sendInput(String address, String type, Object... payload) { ScreenClient.sendInput(ScreenBlockEntity.this, type, payload); }
        @OnlyIn(Dist.CLIENT) @Override public boolean render(TextBuffer buffer, PoseStack pose, MultiBufferSource buffers, int light) { return ScreenTextRenderer.draw(buffer, pose, buffers, light); }
        @Override public int characterWidth() { return ScreenTextRenderer.characterWidth(); }
        @Override public int characterHeight() { return ScreenTextRenderer.characterHeight(); }
    }
}
