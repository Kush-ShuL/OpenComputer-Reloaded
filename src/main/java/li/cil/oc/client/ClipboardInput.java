package li.cil.oc.client;

import li.cil.oc.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Apply the original whole-paste limit and shared cooldown before splitting transport packets. */
public final class ClipboardInput {
    private static long nextPaste;
    private ClipboardInput(){}
    public static String read(){
        Minecraft client=Minecraft.getInstance();String text=client.keyboardHandler.getClipboard();
        if(text==null||text.isEmpty())return null;
        long now=System.currentTimeMillis();
        if(text.length()>Settings.get().maxClipboardTextLength||now<nextPaste){client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HARP,1));return null;}
        nextPaste=now+text.length()/10;return text;
    }
}
