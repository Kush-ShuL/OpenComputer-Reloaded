package li.cil.oc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Opt-in GL diagnostics at actual OC rendering boundaries; repeated errors are rate limited. */
public final class RenderDiagnostics {
    private record Error(String where,int code){}
    private static final Map<Error,Long> LAST=new HashMap<>();
    private static final Map<Error,Integer> SUPPRESSED=new HashMap<>();
    private RenderDiagnostics(){}
    public static void checkError(String where){if(!Settings.get().logOpenGLErrors||!RenderSystem.isOnRenderThread())return;for(int i=0;i<16;i++){int code=GL11.glGetError();if(code==GL11.GL_NO_ERROR)return;Error error=new Error(where,code);long now=System.nanoTime();Long previous=LAST.get(error);if(previous!=null&&now-previous<5_000_000_000L){SUPPRESSED.merge(error,1,Integer::sum);continue;}int suppressed=SUPPRESSED.getOrDefault(error,0);SUPPRESSED.remove(error);LAST.put(error,now);OpenComputers.LOGGER.warn("GL ERROR @ {}: {} (0x{}), {} repeated errors suppressed",where,description(code),Integer.toHexString(code),suppressed);}}
    private static String description(int code){return switch(code){case 0x0500->"invalid enum";case 0x0501->"invalid value";case 0x0502->"invalid operation";case 0x0503->"stack overflow";case 0x0504->"stack underflow";case 0x0505->"out of memory";case 0x0506->"invalid framebuffer operation";case 0x0507->"context lost";default->"unknown OpenGL error";};}
    static void clear(){LAST.clear();SUPPRESSED.clear();}
}
