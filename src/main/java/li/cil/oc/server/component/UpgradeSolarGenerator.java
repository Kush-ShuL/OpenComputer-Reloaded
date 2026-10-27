package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.network.EnvironmentHost;
import java.util.Map;
import net.minecraft.world.level.biome.Biome;

public final class UpgradeSolarGenerator extends UpgradeBase {
    private int ticksUntilCheck;
    private boolean sunny;
    public UpgradeSolarGenerator(EnvironmentHost host){super(host,null,"Solar panel","Enligh10",true);}
    @Override public boolean canUpdate(){return true;}
    @Override public Map<String,String> getDeviceInfo(){
        return Map.of("class","power","description","Solar panel","vendor",li.cil.oc.Constants.DeviceInfo.DefaultVendor,"product","Enligh10");
    }
    @Override public void update(){
        var level=host.world();
        if(level==null||level.isClientSide)return;
        if(--ticksUntilCheck<=0){ticksUntilCheck=100;sunny=isSunVisible();}
        if(sunny)connector().changeBuffer(Settings.get().solarGeneratorEfficiency);
    }
    private boolean isSunVisible(){
        var level=host.world();var pos=position().above();
        // isNether became dimension-type properties; ultraWarm expresses Nether-like behavior, unlike a ceiling.
        // canBlockSeeSky was renamed canSeeSkyFromBelowWater and intentionally permits transparent water below sea level.
        return level.isDay()&&!level.dimensionType().ultraWarm()&&level.canSeeSkyFromBelowWater(pos)
                &&(level.getBiome(pos).value().getPrecipitationAt(pos)!=Biome.Precipitation.RAIN
                ||!level.isRaining()&&!level.isThundering());
    }
}
