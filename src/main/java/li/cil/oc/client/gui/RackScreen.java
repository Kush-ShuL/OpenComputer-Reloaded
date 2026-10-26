package li.cil.oc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import li.cil.oc.common.menu.RackMenu;
import li.cil.oc.common.network.RackNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Original 176x210 rack circuit view, including all four connections for every bay. */
public final class RackScreen extends AbstractContainerScreen<RackMenu> {
    private static final ResourceLocation PANEL=ResourceLocation.fromNamespaceAndPath("opencomputers","textures/gui/rack.png");
    private static final Direction[] BUSES={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.WEST,Direction.EAST};
    private final Button[][][] wires=new Button[4][4][5];
    private Button relay;
    public RackScreen(RackMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=210;inventoryLabelY=116;}
    @Override protected void init(){super.init();relay=addRenderableWidget(Button.builder(Component.empty(),button->PacketDistributor.sendToServer(new RackNetworking.Edit(menu.position,0,0,-1,1,!menu.relayEnabled()))).bounds(leftPos+101,topPos+96,65,18).build());
        for(int slot=0;slot<4;slot++)for(int connection=0;connection<4;connection++)for(int bus=0;bus<5;bus++){int s=slot,c=connection,b=bus;int x=leftPos+45+bus*11,y=topPos+23+slot*20+(connection==0?0:5+(connection-1)*4);wires[slot][connection][bus]=addRenderableWidget(new Button(x,y,3,connection==0?3:2,Component.empty(),button->PacketDistributor.sendToServer(new RackNetworking.Edit(menu.position,s,c,menu.mapping(s,c)==BUSES[b].ordinal()?-1:BUSES[b].ordinal(),0,false)),narration -> narration.get()){@Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partial){if(isHoveredOrFocused())graphics.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),0x80FFFFFF);}});}
    }
    private Component side(Direction side){return Component.translatable("oc:gui.Rack."+switch(side){case DOWN->"Bottom";case UP->"Top";case NORTH->"Back";case WEST->"Right";case EAST->"Left";default->"None";});}
    @Override protected void containerTick(){super.containerTick();relay.setMessage(Component.translatable(menu.relayEnabled()?"oc:gui.Rack.Enabled":"oc:gui.Rack.Disabled"));for(int slot=0;slot<4;slot++)for(int connection=0;connection<4;connection++)for(int bus=0;bus<5;bus++)wires[slot][connection][bus].visible=menu.present(slot,connection);}
    private void blit(GuiGraphics graphics,int x,int y,int u,int v,int width,int height){graphics.blit(PANEL,leftPos+x,topPos+y,u,v,width,height);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);blit(graphics,0,0,0,0,imageWidth,imageHeight);
        for(int slot=0;slot<4;slot++){int cy=23+slot*20;for(int connection=0;connection<4;connection++){boolean master=connection==0;int y=cy+(master?0:5+(connection-1)*4);if(menu.present(slot,connection))blit(graphics,37,y,194,master?26:11,1,master?3:2);int side=menu.mapping(slot,connection),selected=-1;for(int bus=0;bus<5;bus++)if(BUSES[bus].ordinal()==side)selected=bus;if(selected>=0)for(int bus=0;bus<=selected;bus++)blit(graphics,38+bus*11,y,186,master?16+selected*4:1+selected*3,6,master?3:2);for(int bus=0;bus<5;bus++){int x=45+bus*11;if(menu.present(slot,connection))blit(graphics,x-1,y-1,194,master?20:6,5,master?5:4);else blit(graphics,x,y-1,195,master?14:1,3,master?5:4);}}}
        if(menu.relayEnabled())for(int bus=0;bus<4;bus++)blit(graphics,50+bus*11,104,195,30,4,2);RenderSystem.disableBlend();
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY){super.renderLabels(graphics,mouseX,mouseY);for(int bus=0;bus<5;bus++)graphics.drawString(font,side(BUSES[bus]),122,20+bus*11,0x404040,false);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){super.render(graphics,mouseX,mouseY,partial);for(int slot=0;slot<4;slot++)for(int connection=0;connection<4;connection++)for(int bus=0;bus<5;bus++)if(wires[slot][connection][bus].visible&&wires[slot][connection][bus].isMouseOver(mouseX,mouseY))graphics.renderTooltip(font,side(BUSES[bus]),mouseX,mouseY);renderTooltip(graphics,mouseX,mouseY);}
}
