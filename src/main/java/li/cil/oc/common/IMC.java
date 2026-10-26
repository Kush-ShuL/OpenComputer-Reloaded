package li.cil.oc.common;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.common.integration.CallbackMethods;
import li.cil.oc.common.integration.ItemCharge;
import li.cil.oc.common.integration.PeripheralBlacklist;
import li.cil.oc.common.integration.WrenchTools;
import li.cil.oc.common.item.data.PrintData;
import li.cil.oc.common.template.AssemblerTemplates;
import li.cil.oc.common.template.DisassemblerTemplates;
import li.cil.oc.server.driver.Registry;
import li.cil.oc.server.machine.ProgramLocations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;

/** Receiver for the actual public IMC message formats from the original mod. */
public final class IMC {
    public enum State { REGISTERED, REGISTERED_PENDING_DEVICE, REJECTED, UNSUPPORTED }
    public record Registration(String sender, String message, State state, String detail) {}
    private static final CopyOnWriteArrayList<Registration> REGISTRATIONS = new CopyOnWriteArrayList<>();
    private static boolean registered;
    private IMC() {}
    public static synchronized void register(IEventBus bus) { if (!registered) { bus.addListener(IMC::process); registered = true; } }
    public static List<Registration> registrations() { return List.copyOf(REGISTRATIONS); }
    private static void process(InterModProcessEvent event) {
        event.enqueueWork(() -> {
            ProgramLocations.initializeBundled();
            if (Settings.get().config.hasPath("integration.peripheralBlacklist")) Settings.get().config.getStringList("integration.peripheralBlacklist").forEach(PeripheralBlacklist::add);
            InterModComms.getMessages(OpenComputers.ID).forEach(message -> {
                try { handleMessage(message.senderModId(), message.method(), message.messageSupplier().get()); }
                catch (RuntimeException | LinkageError error) { reject(message.senderModId(), message.method(), error); }
            });
        });
    }
    public static void handleMessage(String sender, String method, Object payload) {
        try {
            State state = State.REGISTERED;
            String detail;
            switch (method) {
                case li.cil.oc.api.IMC.REGISTER_ASSEMBLER_FILTER -> { AssemblerTemplates.addFilter(string(payload)); detail = "Applied to the assembler's template selection"; }
                case li.cil.oc.api.IMC.REGISTER_ASSEMBLER_TEMPLATE -> { AssemblerTemplates.add(compound(payload)); detail = "Registered with the assembler's validation, inventory and production flow"; }
                case li.cil.oc.api.IMC.REGISTER_DISASSEMBLER_TEMPLATE -> { DisassemblerTemplates.add(compound(payload)); detail = "Registered with the disassembler's recovery queue and guaranteed-drop flow"; }
                case li.cil.oc.api.IMC.REGISTER_TOOL_DURABILITY_PROVIDER -> { ToolDurabilityProviders.add(callback(string(payload), double.class, ItemStack.class)); detail = "Registered in callable ToolDurabilityProviders.getDurability"; }
                case li.cil.oc.api.IMC.REGISTER_WRENCH_TOOL_CHECK -> { WrenchTools.addCheck(callback(string(payload), boolean.class, ItemStack.class)); detail = "Registered in real wrench item queries"; }
                case li.cil.oc.api.IMC.REGISTER_WRENCH_TOOL -> { WrenchTools.addUsage(callback(string(payload), boolean.class, Player.class, BlockPos.class, boolean.class)); detail = "Registered in real wrench simulation and usage"; }
                case li.cil.oc.api.IMC.REGISTER_ITEM_CHARGE -> { CompoundTag data = compound(payload); Method check = callback(required(data, "canCharge"), boolean.class, ItemStack.class); Method charge = callback(required(data, "charge"), double.class, ItemStack.class, double.class, boolean.class); ItemCharge.add(data.getString("name"), check, charge); detail = "Registered with the charger's item charging flow"; }
                case li.cil.oc.api.IMC.REGISTER_INK_PROVIDER -> { PrintData.addInkProvider(callback(string(payload), int.class, ItemStack.class)); detail = "Registered in the existing PrintData.inkValue consumer"; }
                case li.cil.oc.api.IMC.BLACKLIST_PERIPHERAL -> { PeripheralBlacklist.add(string(payload)); state = State.REGISTERED_PENDING_DEVICE; detail = "Peripheral blacklist retained; ComputerCraft integration driver is not yet ported"; }
                case li.cil.oc.api.IMC.BLACKLIST_HOST -> {
                    CompoundTag data = compound(payload); Class<?> host = Class.forName(required(data, "host"));
                    ResourceLocation id = ResourceLocation.tryParse(required(data.getCompound("item"), "id"));
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || id.equals(ResourceLocation.withDefaultNamespace("air"))) throw new IllegalArgumentException("Host blacklist refers to an unknown or empty registered item");
                    // Registry.blacklistHost intentionally matches Item identity,
                    // not stack components. No world lookup is needed to do so.
                    Registry.INSTANCE.blacklistHost(new ItemStack(BuiltInRegistries.ITEM.get(id)), host);
                    detail = "Applied to the actual item/host driver blacklist";
                }
                case li.cil.oc.api.IMC.REGISTER_PROGRAM_DISK_LABEL -> {
                    CompoundTag data = compound(payload); ArrayList<String> architectures = new ArrayList<>();
                    for (Tag value : data.getList("architectures", Tag.TAG_STRING)) architectures.add(((StringTag) value).getAsString());
                    ProgramLocations.addMapping(required(data, "program"), required(data, "label"), architectures.toArray(String[]::new));
                    detail = "Program hint registered with architecture filtering and factory-backed media resolution";
                }
                case li.cil.oc.api.IMC.REGISTER_CUSTOM_POWER_SYSTEM -> { state = State.UNSUPPORTED; detail = "Upstream defines this constant but provides no payload protocol or receiver; no power integration was fabricated"; }
                default -> { state = State.UNSUPPORTED; detail = "Unknown OpenComputers IMC message"; }
            }
            REGISTRATIONS.add(new Registration(sender, method, state, detail));
            if (state == State.UNSUPPORTED) OpenComputers.LOGGER.warn("Unsupported IMC '{}' from {}: {}", method, sender, detail);
            else OpenComputers.LOGGER.debug("OpenComputers IMC '{}' from {}: {}", method, sender, detail);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) { reject(sender, method, error); }
    }
    private static Method callback(String name, Class<?> returns, Class<?>... signature) throws ReflectiveOperationException { return CallbackMethods.resolve(name, returns, signature); }
    private static CompoundTag compound(Object payload) { if (payload instanceof CompoundTag tag) return tag.copy(); throw new IllegalArgumentException("Expected an NBT compound payload"); }
    private static String string(Object payload) { if (payload instanceof String text && !text.isBlank()) return text; throw new IllegalArgumentException("Expected a non-empty string payload"); }
    private static String required(CompoundTag data, String key) { if (!data.contains(key, Tag.TAG_STRING) || data.getString(key).isBlank()) throw new IllegalArgumentException("Missing IMC string field: " + key); return data.getString(key); }
    private static void reject(String sender, String method, Throwable error) { REGISTRATIONS.add(new Registration(sender, method, State.REJECTED, error.toString())); OpenComputers.LOGGER.warn("Rejected OpenComputers IMC '{}' from {}", method, sender, error); }
}
