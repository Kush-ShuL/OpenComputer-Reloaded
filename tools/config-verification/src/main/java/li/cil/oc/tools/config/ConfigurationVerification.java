package li.cil.oc.tools.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.API;

/** Exercises the real loader and real isolated files; never packaged with the mod. */
public final class ConfigurationVerification {
    private static String group="setup";
    private final Path directory;
    private ConfigurationVerification(Path directory){this.directory=directory;}

    public static void main(String[] args){
        try{
            Path parent=Path.of(System.getProperty("oc.configVerificationDirectory","build/config-verification")).toAbsolutePath();
            Files.createDirectories(parent);Path run=Files.createTempDirectory(parent,"run-");
            System.out.println("CONFIGURATION_VERIFY workspace="+run);
            var verification=new ConfigurationVerification(run);
            group="defaults_and_opt_in";verification.defaultsAndOptIn();passed();
            group="current_snapshot_round_trip";verification.currentSnapshotRoundTrip();passed();
            group="legacy_migration_and_invalid_files";verification.legacyAndInvalidFiles();passed();
            System.out.println("CONFIGURATION_VERIFY status=PASS groups=3");
        }catch(Throwable error){
            System.err.println("CONFIGURATION_VERIFY status=FAIL group="+group+" error="+error);
            error.printStackTrace(System.err);System.exit(1);
        }
    }
    private static void passed(){System.out.println("CONFIGURATION_VERIFY group="+group+" status=PASS");}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void equal(Object actual,Object expected,String message){require(Objects.equals(actual,expected),message+"; expected="+expected+", actual="+actual);}
    private static void close(double actual,double expected,String message){require(Math.abs(actual-expected)<1e-10,message+"; expected="+expected+", actual="+actual);}
    private Path write(String name,String text)throws IOException{Path path=directory.resolve(name);Files.writeString(path,text,StandardCharsets.UTF_8);return path;}
    private static Config document(Path path){return ConfigFactory.parseFile(path.toFile()).resolve();}
    private static Settings load(Path path)throws IOException{
        Settings.load(path);
        // Match the real entrypoint's facade wiring after the real loader has succeeded.
        Settings loaded=Settings.get();API.config=loaded.config;return loaded;
    }
    private static List<Path> backups(Path path)throws IOException{
        try(var files=Files.list(path.getParent())){String prefix=path.getFileName()+".migration-";return files.filter(file->file.getFileName().toString().startsWith(prefix)&&file.getFileName().toString().endsWith(".bak")).sorted().toList();}
    }
    private static void backupEquals(Path path,byte[] original)throws IOException{
        List<Path> backups=backups(path);equal(backups.size(),1,"A successful migration must retain exactly one original backup");
        require(Pattern.matches(Pattern.quote(path.getFileName().toString())+"\\.migration-\\d{8}-\\d{6}-\\d{3}\\.bak",backups.getFirst().getFileName().toString()),"Migration backup name does not contain the agreed UTC timestamp shape");
        require(Arrays.equals(Files.readAllBytes(backups.getFirst()),original),"Migration backup does not preserve the original file bytes");
    }

    private void defaultsAndOptIn()throws Exception{
        Path missing=directory.resolve("new-settings.conf");Settings defaults=load(missing);
        require(Files.isRegularFile(missing),"The loader did not generate a missing configuration");
        require(!defaults.computerCraftEnabled&&!API.config.getBoolean("integration.computerCraft.enabled"),"Fresh defaults unexpectedly enabled ComputerCraft");
        equal(document(missing).getInt("opencomputers.configSchemaVersion"),1,"New configuration schema marker was not written");
        equal(document(missing).getString("opencomputers.version"),OpenComputers.VERSION,"Generated version differs from the running mod");
        require(backups(missing).isEmpty(),"Creating a new configuration backed up a nonexistent original");

        Path enabled=write("explicit-opt-in.conf","""
                opencomputers {
                  version = "0.1.0-SNAPSHOT"
                  integration.computerCraft.enabled = true
                }
                """);
        byte[] original=Files.readAllBytes(enabled);Settings first=load(enabled);
        require(first.computerCraftEnabled&&API.config.getBoolean("integration.computerCraft.enabled"),"Explicit ComputerCraft opt-in was reset");
        backupEquals(enabled,original);Settings second=load(enabled);
        require(second.computerCraftEnabled&&document(enabled).getBoolean("opencomputers.integration.computerCraft.enabled"),"Explicit ComputerCraft opt-in did not survive a second real load");
        equal(backups(enabled).size(),1,"An already migrated configuration was backed up again");
    }

    private void currentSnapshotRoundTrip()throws Exception{
        Path path=write("existing-port.conf","""
                # Existing player tuning in the Java port must survive schema adoption.
                opencomputers {
                  version = "0.1.0-SNAPSHOT"
                  misc.maxWirelessRange = [48, 777]
                  misc.maxClipboard = 4097
                  robot.delays.move = 1.5
                  robot.delays.turn = 0.45
                  robot.delays.use = 0.02
                  power.cost.gpuSet = 16
                  internet.requestTimeout = 17
                  addonFixture {
                    label = "preserve-player-value"
                    numbers = [3, 4, 9]
                  }
                }
                anotherModFixture { enabled = true, token = "outside-oc" }
                """);
        byte[] original=Files.readAllBytes(path);
        for(int pass=1;pass<=2;pass++){
            Settings settings=load(path);
            require(Arrays.equals(settings.maxWirelessRange,new double[]{48,777}),"Existing port wireless tuning was reset on load "+pass);
            equal(settings.maxClipboardTextLength,4097,"Existing port clipboard tuning was reset");
            close(settings.moveDelay,1.44,"Move delay does not use the adjusted runtime value");
            close(settings.turnDelay,.39,"Turn delay does not use the adjusted runtime value");
            close(settings.useDelay,0,"Short use delay was not clamped after its runtime adjustment");
            close(settings.gpuSetCost,.02,"GPU cost did not use its normalized runtime value");
            equal(settings.httpTimeout,17000,"HTTP timeout seconds were not converted to milliseconds");
            close(API.config.getDouble("robot.delays.move"),1.5,"SDK raw move delay was repeatedly reduced");
            close(API.config.getDouble("robot.delays.turn"),.45,"SDK raw turn delay was repeatedly reduced");
            close(API.config.getDouble("robot.delays.use"),.02,"SDK raw short delay was overwritten with its runtime clamp");
            close(API.config.getDouble("power.cost.gpuSet"),16,"SDK raw GPU cost was overwritten with its normalized runtime value");
            equal(API.config.getString("addonFixture.label"),"preserve-player-value","Unknown addon key was discarded");
            equal(API.config.getIntList("addonFixture.numbers"),List.of(3,4,9),"Unknown addon array was discarded");
            Config written=document(path);
            close(written.getDouble("opencomputers.robot.delays.move"),1.5,"Written robot delay accumulated the adjustment");
            equal(written.getString("anotherModFixture.token"),"outside-oc","Unknown top-level addon object was discarded");
        }
        backupEquals(path,original);
    }

    private void legacyAndInvalidFiles()throws Exception{
        Path path=write("legacy-1.12.conf","""
                # Original Minecraft 1.12 configuration; preserve custom integration entries.
                opencomputers {
                  version = "1.8.2"
                  internet {
                    blacklist = ["10.42.0.0/16", "blocked.example.invalid"]
                    whitelist = ["203.0.113.7", "allowed.example.invalid"]
                    filteringRules = ["deny ipv6", "allow default"]
                  }
                  nanomachines.potionWhitelist = [0, 1, 5, 27, "addon:configured_effect"]
                  integration.peripheralBlacklist = [
                    "net.minecraft.tileentity.TileEntityCommandBlock",
                    "addon.compat.Peripheral",
                    "net.minecraft.tileentity.TileEntityCommandBlock"
                  ]
                  power.value.RedstoneFlux = 900
                  addonFixture.marker = "legacy-kept"
                }
                """);
        byte[] original=Files.readAllBytes(path);Settings migrated=load(path);
        equal(API.config.getStringList("internet.filteringRules"),List.of("deny ip:10.42.0.0/16","deny domain:blocked.example.invalid","allow ip:203.0.113.7","allow domain:allowed.example.invalid","deny all","deny ipv6","allow default"),"Legacy HTTP lists did not preserve ordered deny/allow rules and the user tail");
        require(API.config.getStringList("internet.blacklist").isEmpty()&&API.config.getStringList("internet.whitelist").isEmpty(),"Removed HTTP option values were not cleared after migration");
        equal(API.config.getStringList("nanomachines.potionWhitelist"),List.of("minecraft:speed","minecraft:strength","minecraft:unluck","addon:configured_effect"),"Vanilla numeric potion ids did not migrate to their stable effect names");
        equal(API.config.getStringList("integration.peripheralBlacklist"),List.of("net.minecraft.world.level.block.entity.CommandBlockEntity","addon.compat.Peripheral"),"Legacy peripheral class migration lost order, custom names or deduplication");
        close(API.config.getDouble("power.value.ForgeEnergy"),900,"Legacy RF tuning was replaced instead of inherited by FE");
        close(migrated.ratioForgeEnergy,.9,"Migrated FE value was not scaled into its runtime conversion ratio");
        require(!migrated.computerCraftEnabled,"A legacy configuration implicitly enabled the new CC integration");
        equal(API.config.getString("addonFixture.marker"),"legacy-kept","Legacy addon key was removed");
        require(API.config.hasPath("computer.threads")&&document(path).hasPath("opencomputers.integration.computerCraft.enabled"),"Missing configuration entries were not filled and written");
        backupEquals(path,original);load(path);equal(backups(path).size(),1,"Migrated legacy configuration was patched and backed up again");

        invalidFile("malformed.conf","opencomputers { version = \"0.1.0-SNAPSHOT\"\n robot { delays { move = \n",ConfigException.Parse.class,null,null);
        invalidFile("wrong-type.conf","""
                opencomputers {
                  version = "0.1.0-SNAPSHOT"
                  computer.threads = "many"
                }
                """,ConfigException.WrongType.class,"computer.threads",null);
        invalidFile("unknown-potion.conf","""
                # This unknown legacy id must remain reviewable after the error.
                opencomputers {
                  version = "1.8.10"
                  nanomachines.potionWhitelist = [1, 99]
                }
                """,ConfigException.BadValue.class,"opencomputers.nanomachines.potionWhitelist","no reliable vanilla 1.12-to-1.21 mapping");
        arrayFallbacks();
    }
    private void invalidFile(String name,String text,Class<? extends Throwable> expectedCause,String pathHint,String reasonHint)throws Exception{
        Path path=write(name,text);byte[] original=Files.readAllBytes(path);Settings previous=Settings.get();Config previousApi=API.config;IOException failure=null;
        try{load(path);}catch(IOException expected){failure=expected;}
        require(failure!=null,"Invalid file was silently accepted: "+name);
        require(failure.getMessage().contains(path.toAbsolutePath().toString())&&failure.getMessage().contains("left unchanged"),"Configuration error omitted the original path or retention instruction: "+failure);
        require(expectedCause.isInstance(failure.getCause()),"Wrong underlying configuration error type for "+name+": "+failure.getCause());
        if(pathHint!=null)require(failure.getMessage().contains(pathHint),"Configuration error omitted the failing option: "+failure);
        if(reasonHint!=null){require(failure.getMessage().contains(reasonHint)&&failure.getMessage().contains("99"),"Unknown numeric potion error omitted its id or migration reason: "+failure);}
        require(Arrays.equals(Files.readAllBytes(path),original),"Invalid configuration bytes were changed: "+name);
        require(backups(path).isEmpty(),"Invalid configuration was backed up as though migration had succeeded: "+name);
        require(Settings.get()==previous&&API.config==previousApi,"Failed validation replaced the last valid settings/facade");
    }
    private void arrayFallbacks()throws Exception{
        Path path=write("array-fallbacks.conf","""
                opencomputers {
                  version = "0.1.0-SNAPSHOT"
                  configSchemaVersion = 1
                  misc.maxWirelessRange = [12]
                  computer.cpuComponentCount = [3, 6]
                  filesystem.hddPlatterCounts = [2]
                  misc.maxOpenPorts = [-2, 7, -5]
                  power.cost.wirelessCostPerRange = [-0.2, 1.25]
                }
                """);
        Settings settings=load(path);
        require(Arrays.equals(settings.maxWirelessRange,new double[]{16,400}),"Wrong-length wireless range did not use the original fixed fallback");
        require(Arrays.equals(settings.cpuComponentSupport,new int[]{8,12,16,1024}),"Wrong-length CPU support did not use the original fixed fallback");
        require(Arrays.equals(settings.hddPlatterCounts,new int[]{2,4,6}),"Wrong-length HDD platters did not use the original fallback (which differs from normal defaults)");
        require(Arrays.equals(settings.maxOpenPorts,new int[]{0,7,0})&&Arrays.equals(settings.wirelessCostPerRange,new double[]{0,1.25}),"Valid-length arrays did not apply their original per-entry clamps");
        equal(API.config.getIntList("misc.maxWirelessRange"),List.of(12),"Fallback runtime array overwrote the configured raw array");
        equal(document(path).getIntList("opencomputers.computer.cpuComponentCount"),List.of(3,6),"Fallback runtime values overwrote the invalid-length source array");
        close(API.config.getDoubleList("power.cost.wirelessCostPerRange").getFirst(),-.2,"Array clamp overwrote the negative raw entry");
    }
}
