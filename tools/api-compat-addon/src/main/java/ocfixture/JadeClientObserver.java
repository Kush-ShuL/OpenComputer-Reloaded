package ocfixture;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Observes Jade's normal collected and rendered HUD without registering another provider. */
@WailaPlugin("jade")
public final class JadeClientObserver implements IWailaPlugin {
    @Override public void registerClient(IWailaClientRegistration registration){
        JadeClientContract.observerRegistered();
        registration.addTooltipCollectedCallback(JadeClientContract::collected);
        registration.addAfterRenderCallback((box,rect,graphics,accessor)->JadeClientContract.rendered(box,accessor));
    }
}
