package li.cil.oc.common.item;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import li.cil.oc.OpenComputers;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.network.Analyzable;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.event.RobotAnalyzeEvent;
import li.cil.oc.common.block.entity.RobotBlockEntity;
import li.cil.oc.common.block.entity.ScreenBlockEntity;
import li.cil.oc.common.capabilities.Capabilities;
import li.cil.oc.common.network.ToolNetworking;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Original node diagnostics and the analyzer's in-item screen clipboard. */
@EventBusSubscriber(modid = OpenComputers.ID)
public final class AnalyzerItem extends Item {
    @FunctionalInterface public interface ClientActions {
        boolean copyScreenLine(ScreenBlockEntity screen, Vec3 point, InteractionHand hand);
    }
    private static volatile ClientActions clientActions;
    public AnalyzerItem(Properties properties) { super(properties); }
    public static void setClientActions(ClientActions actions) { clientActions = Objects.requireNonNull(actions); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            clearClipboard(stack);
            if (!level.isClientSide) player.getInventory().setChanged();
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !context.getLevel().hasChunkAt(context.getClickedPos())) return InteractionResult.PASS;
        BlockEntity entity = context.getLevel().getBlockEntity(context.getClickedPos());
        if (entity instanceof ScreenBlockEntity screen && context.getClickedFace() == screen.facing()) {
            if (player.isShiftKeyDown()) {
                // The physical client's font metrics determine the clicked text row.
                // The server independently checks the held tool, reach and screen face.
                if (context.getLevel().isClientSide && clientActions != null)
                    return clientActions.copyScreenLine(screen, context.getClickLocation(), context.getHand()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
                return InteractionResult.CONSUME;
            }
            CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!data.contains("oc:clipboard")) return InteractionResult.FAIL;
            if (!context.getLevel().isClientSide) screen.origin().buffer().clipboard(data.getString("oc:clipboard"), player);
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        Vec3 relative = context.getClickLocation().subtract(Vec3.atLowerCornerOf(context.getClickedPos()));
        return analyze(entity, player, context.getClickedFace(), (float) relative.x, (float) relative.y, (float) relative.z)
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide) : InteractionResult.PASS;
    }

    @SubscribeEvent public static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity().getItemInHand(event.getHand()).getItem() instanceof AnalyzerItem)) return;
        if (analyze(event.getTarget(), event.getEntity(), Direction.DOWN, 0, 0, 0)) {
            event.getEntity().swing(event.getHand());
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        }
    }

    public static boolean analyze(Object target, Player player, Direction side, float x, float y, float z) {
        if (target instanceof Analyzable analyzable) {
            if (!player.level().isClientSide) {
                // The controller is the original robot host; its moving block entity is only a proxy.
                // Fire once before generic node diagnostics, as in the original Robot.onAnalyze.
                if (target instanceof RobotBlockEntity robot)
                    NeoForge.EVENT_BUS.post(new RobotAnalyzeEvent(robot.controller(), player));
                analyzeNodes(analyzable.onAnalyze(player, side, x, y, z), player);
            }
            return true;
        }
        SidedEnvironment sided = target instanceof SidedEnvironment environment ? environment : null;
        Environment environment = target instanceof Environment value ? value : null;
        if (target instanceof BlockEntity entity && entity.getLevel() != null) {
            if (sided == null) sided = entity.getLevel().getCapability(Capabilities.SidedEnvironmentCapability, entity.getBlockPos(), side);
            if (environment == null) environment = entity.getLevel().getCapability(Capabilities.EnvironmentCapability, entity.getBlockPos(), side);
        }
        if (sided != null) {
            if (!player.level().isClientSide) analyzeNodes(new Node[]{sided.sidedNode(side)}, player);
            return true;
        }
        if (environment != null) {
            if (!player.level().isClientSide) analyzeNodes(new Node[]{environment.node()}, player);
            return true;
        }
        return false;
    }

    private static void analyzeNodes(Node[] nodes, Player player) {
        if (nodes == null || player instanceof FakePlayer || !(player instanceof ServerPlayer serverPlayer)) return;
        for (Node node : nodes) {
            if (node == null) continue;
            if (node.host() instanceof Machine machine) {
                if (machine.lastError() != null) {
                    String error = machine.lastError();
                    serverPlayer.displayClientMessage(message("LastError", Component.translatable(error.startsWith("gui.") ? "oc:" + error : error)), false);
                }
                serverPlayer.displayClientMessage(message("Components", machine.componentCount() + "/" + machine.maxComponents()), false);
                if (machine.users().length > 0) serverPlayer.displayClientMessage(message("Users", String.join(", ", machine.users())), false);
            }
            if (node instanceof Connector connector) {
                if (connector.localBufferSize() > 0) serverPlayer.displayClientMessage(message("StoredEnergy", energy(connector.localBuffer(), connector.localBufferSize())), false);
                serverPlayer.displayClientMessage(message("TotalEnergy", energy(connector.globalBuffer(), connector.globalBufferSize())), false);
            }
            if (node instanceof li.cil.oc.api.network.Component component)
                serverPlayer.displayClientMessage(message("ComponentName", component.name()), false);
            String address = node.address();
            if (address != null && !address.isEmpty()) {
                serverPlayer.displayClientMessage(message("Address", address).withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, address))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, message("CopyToClipboard")))), false);
                ToolNetworking.sendAnalyze(address, serverPlayer);
            }
        }
    }
    private static net.minecraft.network.chat.MutableComponent message(String key, Object... args) { return Component.translatable("oc:gui.Analyzer." + key, args); }
    private static String energy(double stored, double capacity) { return String.format(Locale.ROOT, "%.2f/%.2f", stored, capacity); }
    private static void clearClipboard(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains("oc:clipboard")) return;
        data.remove("oc:clipboard");
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA); else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }
    public static void copyLine(ScreenBlockEntity screen, int line, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof AnalyzerItem)) return;
        clearClipboard(stack);
        var buffer = screen.origin().buffer();
        if (line >= 0 && line < buffer.getViewportHeight()) {
            String text = buffer.data().lineToString(line);
            if (!text.isEmpty()) CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putString("oc:clipboard", text));
        }
        player.getInventory().setChanged();
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("oc:tooltip.analyzer"));
    }
}
