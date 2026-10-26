package li.cil.oc.server.component;

import java.util.LinkedHashMap;
import java.util.Map;
import li.cil.oc.Settings;

/** The original integrated GPU, retaining GraphicsCard callbacks and VRAM. */
public final class APU extends GraphicsCard {
    public APU(int graphicsTier) { super(graphicsTier); }
    @Override public Map<String, String> getDeviceInfo() {
        Map<String, String> info = new LinkedHashMap<>(super.getDeviceInfo());
        info.put("class", "processor");
        info.put("description", "APU");
        info.put("product", "FlexiArch " + (tier + 1) + " Processor (Builtin Graphics)");
        info.put("clock", (int) (Settings.get().callBudgets[tier] * 1000) + "+" + info.get("clock"));
        return Map.copyOf(info);
    }
}
