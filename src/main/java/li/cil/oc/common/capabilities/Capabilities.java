package li.cil.oc.common.capabilities;

import li.cil.oc.api.internal.Colored;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.SidedComponent;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.network.Node;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** The 1.21 capability bridge for OC environments exposed by any block entity. */
@EventBusSubscriber(modid = "opencomputers", bus = EventBusSubscriber.Bus.MOD)
public final class Capabilities {
    public static final BlockCapability<Colored, Direction> ColoredCapability =
        BlockCapability.createSided(id("colored"), Colored.class);
    public static final BlockCapability<Environment, Direction> EnvironmentCapability =
        BlockCapability.createSided(id("environment"), Environment.class);
    public static final BlockCapability<SidedEnvironment, Direction> SidedEnvironmentCapability =
        BlockCapability.createSided(id("sided_environment"), SidedEnvironment.class);

    private Capabilities() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("opencomputers", path);
    }

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        // Register for external block entities too, as the original attachment
        // event did. Returning null leaves unrelated block entities unaffected.
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            registerType(event, type);
        }
    }

    private static <T extends BlockEntity> void registerType(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, type,
            (entity, side) -> entity instanceof EnergyProvider provider ? provider.energy(side) : null);
        event.registerBlockEntity(EnvironmentCapability, type,
            (entity, side) -> entity instanceof Environment environment ? environment : SimpleComponentBridge.environment(entity));
        event.registerBlockEntity(ColoredCapability, type,
            (entity, side) -> entity instanceof Colored colored ? colored : null);
        event.registerBlockEntity(SidedEnvironmentCapability, type, (entity, side) -> {
            if (entity instanceof SidedComponent sided && entity instanceof Environment environment) {
                return new SidedEnvironment() {
                    @Override public Node sidedNode(Direction direction) {
                        return sided.canConnectNode(direction) ? environment.node() : null;
                    }
                    @Override public boolean canConnect(Direction direction) {
                        return sided.canConnectNode(direction);
                    }
                };
            }
            if (entity instanceof SidedComponent sided) {
                Environment environment = SimpleComponentBridge.environment(entity);
                if (environment != null) return new SidedEnvironment() {
                    @Override public Node sidedNode(Direction direction) { return sided.canConnectNode(direction) ? environment.node() : null; }
                    @Override public boolean canConnect(Direction direction) { return sided.canConnectNode(direction); }
                };
            }
            return entity instanceof SidedEnvironment environment ? environment : null;
        });
    }
}
