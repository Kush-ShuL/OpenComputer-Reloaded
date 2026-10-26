package li.cil.oc.client.gui;

import java.text.DecimalFormat;
import li.cil.oc.common.menu.RelayMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Original four-slot upgrade tab and queue/throughput display. */
public final class RelayScreen extends AbstractContainerScreen<RelayMenu> {
    private static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("opencomputers","textures/"+name+".png");}
    private static final ResourceLocation BACKGROUND=texture("gui/background"),TAB=texture("gui/upgrade_tab"),SLOT=texture("gui/slot");
    private static final ResourceLocation[] ICONS={texture("icons/cpu"),texture("icons/memory"),texture("icons/hdd"),texture("icons/card")};
    private final DecimalFormat frequency=new DecimalFormat("#.##'hz'");
    public RelayScreen(RelayMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=199;imageHeight=166;}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){
        graphics.blit(BACKGROUND,leftPos,topPos,0,0,176,166);
        graphics.blit(TAB,leftPos+176,topPos+10,0,0,23,26,23,26);
        int[][] positions={{151,15},{151,34},{151,53},{178,15}};
        for(int i=0;i<4;i++){int x=leftPos+positions[i][0],y=topPos+positions[i][1];graphics.blit(SLOT,x-1,y-1,0,0,18,18,18,18);if(!menu.slots.get(i).hasItem())graphics.blit(ICONS[i],x,y,0,0,16,16,16,16);}
    }
    private int color(int value,int yellow,int red){return value<yellow?0x009900:value<red?0x999900:0x990000;}
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY){
        super.renderLabels(graphics,mouseX,mouseY);
        String[] keys={"TransferRate","PacketsPerCycle","QueueSize"};for(int i=0;i<3;i++)graphics.drawString(font,Component.translatable("oc:gui.Switch."+keys[i]),14,20+i*19,0x404040,false);
        int delay=Math.max(1,menu.statistic(0)),amount=menu.statistic(1),maximum=menu.statistic(2),average=menu.statistic(3),queued=menu.statistic(4);
        graphics.drawString(font,frequency.format(20.0/delay),108,20,0x404040,false);
        graphics.drawString(font,average+" / "+amount,108,39,color(average,(amount+1)/2,amount),false);
        graphics.drawString(font,queued+" / "+maximum,108,58,color(queued,maximum/2,maximum),false);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){super.render(graphics,mouseX,mouseY,partial);renderTooltip(graphics,mouseX,mouseY);}
}
