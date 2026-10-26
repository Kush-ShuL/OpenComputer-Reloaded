package li.cil.oc.common;

/** Original zero-based case inventory layouts and component tier limits. */
public final class InventorySlots {
    private InventorySlots(){}
    public record InventorySlot(String slot,int tier){}
    private static InventorySlot s(String name,int tier){return new InventorySlot(name,tier);}
    public static final InventorySlot[][] computer={
        {s("card",0),s("card",0),s("memory",0),s("hdd",0),s("cpu",0),s("memory",0),s("eeprom",Integer.MAX_VALUE)},
        {s("card",1),s("card",0),s("memory",1),s("memory",1),s("hdd",1),s("hdd",0),s("cpu",1),s("eeprom",Integer.MAX_VALUE)},
        {s("card",2),s("card",1),s("card",1),s("memory",2),s("memory",2),s("hdd",2),s("hdd",1),s("floppy",0),s("cpu",2),s("eeprom",Integer.MAX_VALUE)},
        {s("card",2),s("card",2),s("card",2),s("memory",2),s("memory",2),s("hdd",2),s("hdd",2),s("floppy",0),s("cpu",2),s("eeprom",Integer.MAX_VALUE)}
    };
}
