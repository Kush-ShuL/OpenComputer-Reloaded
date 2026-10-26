package li.cil.oc.config;

import com.mojang.authlib.GameProfile;
import com.typesafe.config.*;
import li.cil.oc.OpenComputers;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Validation helpers for the existing HOCON settings, without rewriting users' effective values. */
public final class SettingsValidation {
    private static final String[] LEGACY_POTIONS={"", "speed","slowness","haste","mining_fatigue","strength","instant_health","instant_damage","jump_boost","nausea","regeneration","resistance","fire_resistance","water_breathing","invisibility","blindness","night_vision","hunger","weakness","poison","wither","health_boost","absorption","saturation","glowing","levitation","luck","unluck"};
    private SettingsValidation(){}
    public static Config prepare(Config config){
        // This opt-in did not exist upstream; an old configuration must not enable it.
        if(!config.hasPath("integration.computerCraft.enabled"))config=config.withValue("integration.computerCraft.enabled",ConfigValueFactory.fromAnyRef(false));
        config.getBoolean("integration.computerCraft.enabled");
        if(config.hasPath("configSchemaVersion")){double schema=number(config,"configSchemaVersion");if(schema!=Math.floor(schema)||schema<0||schema>SettingsMigration.SCHEMA_VERSION)throw bad(config,"configSchemaVersion","an integer in 0.."+SettingsMigration.SCHEMA_VERSION);}
        return config;
    }
    public static ConfigException.BadValue bad(Config config,String path,String expected){ConfigValue value=config.getValue(path);return new ConfigException.BadValue(value.origin(),"opencomputers."+path,"value "+value.render(ConfigRenderOptions.concise())+"; expected "+expected);}
    public static double number(Config config,String path){double value=config.getDouble(path);if(!Double.isFinite(value))throw bad(config,path,"a finite number");return value;}
    public static int integer(Config config,String path){int value=config.getInt(path);number(config,path);return value;}
    private static void length(Config config,String path,int actual,int expected,Object fallback){OpenComputers.LOGGER.warn("Invalid opencomputers.{}: value {} has {} entries; expected exactly {}. Using upstream fallback {} for this setting; configured value is retained in the file.",path,config.getValue(path).render(ConfigRenderOptions.concise()),actual,expected,fallback);}
    public static int[] ints(Config config,String path,int... fallback){var values=config.getIntList(path);if(values.size()!=fallback.length){length(config,path,values.size(),fallback.length,Arrays.toString(fallback));return fallback.clone();}for(double value:config.getDoubleList(path))if(!Double.isFinite(value))throw bad(config,path,"finite integers");return values.stream().mapToInt(Integer::intValue).toArray();}
    public static double[] doubles(Config config,String path,double... fallback){var values=config.getDoubleList(path);if(values.size()!=fallback.length){length(config,path,values.size(),fallback.length,Arrays.toString(fallback));return fallback.clone();}for(double value:values)if(!Double.isFinite(value))throw bad(config,path,"finite numbers");return values.stream().mapToDouble(Double::doubleValue).toArray();}
    public static int color(Config config){try{return Integer.decode(config.getString("client.monochromeColor"));}catch(NumberFormatException error){throw bad(config,"client.monochromeColor","an integer color such as 0xFFFFFF");}}
    public static GameProfile fakePlayer(Config config){try{return new GameProfile(UUID.fromString(config.getString("integration.fakePlayerUuid")),config.getString("integration.fakePlayerName"));}catch(IllegalArgumentException error){throw bad(config,"integration.fakePlayerUuid","a valid UUID such as 7e506b5d-2ccb-4ac4-a249-5624925b0c67");}}
    /** Numeric potion ids were fixed vanilla ids before the registry-based modern API. */
    public static List<String> potionWhitelist(Config config){List<String> result=new ArrayList<>();List<?> values=config.getAnyRefList("nanomachines.potionWhitelist");for(int i=0;i<values.size();i++){Object value=values.get(i);if(value instanceof String name)result.add(name);else if(value instanceof Number number&&number.doubleValue()==number.intValue()){int id=number.intValue();if(id==0)continue;if(id>0&&id<LEGACY_POTIONS.length)result.add("minecraft:"+LEGACY_POTIONS[id]);else throw bad(config,"nanomachines.potionWhitelist","effect resource names; legacy numeric entry "+(i+1)+" ("+id+") has no reliable vanilla 1.12-to-1.21 mapping");}else throw bad(config,"nanomachines.potionWhitelist","effect resource names, or known vanilla 1.12 integer ids 1..27 (invalid entry "+(i+1)+")");}return List.copyOf(result);}
    /** 1.12 numeric dimensions and modern resource ids can coexist during migration. */
    public static List<Object> dimensions(Config config,String path){if(!config.hasPath(path))return List.of();List<Object> result=new ArrayList<>();for(Object value:config.getAnyRefList(path)){if(value instanceof Number number&&number.doubleValue()==number.intValue()){int id=number.intValue();if(id!=-1&&id!=0&&id!=1)throw bad(config,path,"vanilla dimension ids -1, 0 or 1, or namespaced dimension resources; custom legacy dimension id "+id+" requires an explicit resource-name migration");result.add(id);}else if(value instanceof String name&&ResourceLocation.tryParse(name)!=null)result.add(ResourceLocation.parse(name).toString());else throw bad(config,path,"vanilla dimension ids -1, 0 or 1, or valid namespaced dimension resource ids");}return List.copyOf(result);}
    public static String debugMode(Config config){Object value=config.getValue("debug.debugCardAccess").unwrapped();return switch(String.valueOf(value).toLowerCase(Locale.ROOT)){case "true","allow"->"allow";case "false","deny"->"deny";case "whitelist"->"whitelist";default->{OpenComputers.LOGGER.warn("Invalid opencomputers.debug.debugCardAccess value {}; using deny. Expected allow, deny, whitelist, true or false.",value);yield "deny";}};}
}
