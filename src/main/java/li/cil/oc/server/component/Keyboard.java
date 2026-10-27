package li.cil.oc.server.component;

import li.cil.oc.Constants;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Player-checked keyboard messages with persistent press/release pairing. */
public class Keyboard extends AbstractManagedEnvironment implements li.cil.oc.api.internal.Keyboard, DeviceInfo {
    public final EnvironmentHost host;
    public final Map<Player, Map<Integer, Character>> pressedKeys = new WeakHashMap<>();
    private UsabilityChecker usableOverride;
    public Keyboard(EnvironmentHost host) {
        this.host = host;
        setNode(Network.newNode(this, Visibility.Network).withComponent("keyboard").create());
    }
    @Override public void setUsableOverride(UsabilityChecker callback) { usableOverride = callback; }
    @Override public Map<String, String> getDeviceInfo() {
        return Map.of(DeviceAttribute.Class, DeviceClass.Input, DeviceAttribute.Description, "Keyboard",
                DeviceAttribute.Vendor, Constants.DeviceInfo.DefaultVendor, DeviceAttribute.Product, "Fancytyper MX-Stone");
    }
    public boolean isUsableByPlayer(Player player) {
        return player != null && !player.isRemoved() && !player.isSpectator() &&
                (usableOverride != null ? usableOverride.isUsableByPlayer(this, player) :
                        player.level() == host.world() && player.distanceToSqr(host.xPosition(), host.yPosition(), host.zPosition()) <= 64);
    }
    public synchronized void releasePressedKeys(Player player) {
        Map<Integer, Character> keys = pressedKeys.remove(player);
        if (keys == null) return;
        for (var entry : keys.entrySet()) keyboardSignal(player, "key_up", entry.getValue(), entry.getKey());
    }
    @Override public boolean canUpdate() { return true; }
    @Override public synchronized void update() {
        for (Player player : pressedKeys.keySet().toArray(Player[]::new)) if (!isUsableByPlayer(player)) releasePressedKeys(player);
    }
    @Override public synchronized void onDisconnect(Node disconnected) {
        if (disconnected == node()) for (Player player : pressedKeys.keySet().toArray(Player[]::new)) releasePressedKeys(player);
    }
    @Override public synchronized void onMessage(Message message) {
        Object[] values = message.data();
        if (values.length == 3 && values[0] instanceof Player player && values[1] instanceof Character character && values[2] instanceof Integer code) {
            if (message.name().equals("keyboard.keyDown") && isUsableByPlayer(player)) {
                pressedKeys.computeIfAbsent(player, ignored -> new HashMap<>()).put(code, character);
                keyboardSignal(player, "key_down", character, code);
            } else if (message.name().equals("keyboard.keyUp")) {
                Map<Integer, Character> keys = pressedKeys.get(player);
                if (keys != null && keys.remove(code) != null) keyboardSignal(player, "key_up", character, code);
            }
        } else if (values.length == 2 && values[0] instanceof Player player && values[1] instanceof String text &&
                message.name().equals("keyboard.clipboard") && isUsableByPlayer(player)) {
            for (int start = 0; start < text.length();) {
                int end = start;
                while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
                if (end < text.length()) {
                    char separator = text.charAt(end++);
                    if (separator == '\r' && end < text.length() && text.charAt(end) == '\n') end++;
                }
                keyboardSignal(player, "clipboard", text.substring(start, end)); start = end;
            }
        }
    }
    private void keyboardSignal(Player player, String name, Object... values) {
        Object[] payload = new Object[values.length + (Settings.get().inputUsername ? 3 : 2)];
        payload[0] = player; payload[1] = name; System.arraycopy(values, 0, payload, 2, values.length);
        if (Settings.get().inputUsername) payload[payload.length - 1] = player.getGameProfile().getName();
        if (node() != null) node().sendToReachable("computer.checked_signal", payload);
    }
}
