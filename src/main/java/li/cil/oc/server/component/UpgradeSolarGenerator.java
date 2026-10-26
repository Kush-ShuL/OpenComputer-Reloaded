package li.cil.oc.server.component;

import li.cil.oc.Settings;
import li.cil.oc.api.network.EnvironmentHost;

public final class UpgradeSolarGenerator extends UpgradeBase {
    private boolean sunny;
    public UpgradeSolarGenerator(EnvironmentHost host){super(host,null,"Solar panel","Enligh10",true);}
    @Override public boolean canUpdate(){return true;}
    @Override public void update(){var level=host.world();if(level.getGameTime()%100==0){var pos=position().above();sunny=level.isDay()&&!level.dimensionType().hasCeiling()&&level.canSeeSky(pos)&&(!level.getBiome(pos).value().hasPrecipitation()||!level.isRaining()&&!level.isThundering());}if(sunny)connector().changeBuffer(Settings.get().solarGeneratorEfficiency);}
}
