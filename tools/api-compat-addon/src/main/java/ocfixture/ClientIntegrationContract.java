package ocfixture;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import li.cil.oc.api.API;
import li.cil.oc.api.Items;
import li.cil.oc.api.Manual;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.lwjgl.glfw.GLFW;
import org.slf4j.*;

/** Normal owned client world, real JEI screens/recipes, sockets and actual disk payloads. */
public final class ClientIntegrationContract {
    private static final Logger LOG=LoggerFactory.getLogger("oc-client-integration-contract");
    private static final List<String> results=new ArrayList<>();
    private static Path directory,report;private static String runId;private static long started;
    private static int phase,frame,phaseFrame,scan;private static boolean finished;
    private static CompletableFuture<Void> work;private static BlockPos casePos;
    private static RecipeType<?> manuals,callbacks;private static List<?> pages;private static AbstractContainerScreen<?> sockets;
    private static NativeImage beforeHover;private static int hoverX,hoverY;
    private static String priorPage;private static int pageClicks;
    private ClientIntegrationContract(){}
    public static void register(){
        directory=Path.of(System.getProperty("ocfixture.integrationClientDirectory")).toAbsolutePath().normalize();
        report=Path.of(System.getProperty("ocfixture.integrationClientReport")).toAbsolutePath().normalize();
        runId=System.getProperty("ocfixture.integrationClientRunId");require(report.getParent().equals(directory),"Wrong owned report directory");started=System.nanoTime();
        NeoForge.EVENT_BUS.addListener(ClientIntegrationContract::tick);NeoForge.EVENT_BUS.addListener(ClientIntegrationContract::rendered);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,ClientIntegrationContract::worldResources);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ItemStack item(String name){var info=Items.get(name);require(info!=null,"Missing actual item "+name);return info.createItemStack(1);}
    private static void advance(int next){phase=next;phaseFrame=frame;}
    private static void worldResources(LevelEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel level)||level.dimension()!=Level.OVERWORLD)return;
        Path world=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if(!world.equals(directory.resolve("saves/integration-"+runId)))return;
        try{
            Path loot=world.resolve("opencomputers/loot");Files.createDirectories(loot.resolve("openos"));
            Files.writeString(loot.resolve("loot.properties"),"openos=JEI world OpenOS:1:cyan\n",StandardCharsets.ISO_8859_1);
            Files.writeString(loot.resolve("openos/autorun.lua"),"print('JEI world disk acceptance')\n",StandardCharsets.UTF_8);
        }catch(Exception error){throw new IllegalStateException(error);}
    }
    private static void tick(ClientTickEvent.Post event){
        if(finished)return;Minecraft client=Minecraft.getInstance();
        try{
            require(System.nanoTime()-started<TimeUnit.MINUTES.toNanos(6),"Timed out phase="+phase+", screen="+(client.screen==null?"null":client.screen.getClass()));
            if(phase==0&&client.getOverlay()==null&&client.screen instanceof TitleScreen){
                require(client.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()),"Wrong game directory");
                require(!Files.exists(directory.resolve("saves/integration-"+runId)),"A fresh owned world is required");advance(1);
                client.createWorldOpenFlows().createFreshLevel("integration-"+runId,new LevelSettings("Client integration "+runId,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(258943363L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
            }else if(phase==1&&client.player!=null&&client.level!=null&&client.screen==null&&client.getOverlay()==null&&ClientJeiProbe.runtime!=null){
                if(System.getProperty("ocfixture.integrationClientFocus","all").equals("sound")){ComputerSoundClientContract.start(client,directory,results);advance(30);return;}
                IJeiRuntime runtime=ClientJeiProbe.runtime;
                if(!worldDiskVisible(runtime))return;
                verifyClientWorld(client);
                verifyCatalog(runtime);manuals=runtime.getRecipeManager().getRecipeType(ResourceLocation.parse("opencomputers:manual")).orElseThrow();callbacks=runtime.getRecipeManager().getRecipeType(ResourceLocation.parse("opencomputers:api")).orElseThrow();
                var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT,VanillaTypes.ITEM_STACK,item("graphicscard3"));
                pages=lookup(runtime,callbacks,List.of(focus));require(pages.size()>1,"Actual GPU callback documentation did not paginate");
                for(Object page:pages){var method=page.getClass().getDeclaredMethod("lines");method.setAccessible(true);require(((List<?>)method.invoke(page)).size()<=12,"Actual callback page exceeds the original twelve-line limit");}
                show(runtime,callbacks,pages,List.of(focus));advance(2);
            }else if(phase==7&&work.isDone()){
                work.join();if(!client.level.getBlockState(casePos).is(Items.get("case3").block()))return;
                client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(casePos),Direction.UP,casePos,false));advance(8);
            }else if(phase==8&&client.screen instanceof AbstractContainerScreen<?> screen&&screen.getMenu().slots.stream().anyMatch(slot->!(slot.container instanceof net.minecraft.world.entity.player.Inventory)&&slot.mayPlace(item("cpu1")))){
                sockets=screen;ClientJeiProbe.runtime.getIngredientFilter().setFilterText(item("cpu1").getHoverName().getString());advance(9);
            }else if(phase==14&&work.isDone()){
                work.join();if(worldDiskVisible(ClientJeiProbe.runtime))return;
                require(ClientJeiProbe.runtime.getIngredientManager().getAllItemStacks().stream().anyMatch(stack->factory(stack).equals("opencomputers:openos")&&!stack.getHoverName().getString().equals("JEI world OpenOS")),"Cleared world snapshot did not restore the bundled disk");
                results.add("PASS I10.disks actual world loot.properties overrides a bundled factory on the client; a real production disk snapshot clears the override and restores its bundled JEI ingredient");
                client.setScreen(null);JadeClientContract.start(client,directory,results);advance(20);
            }else if(phase==20){JadeClientContract.tick();if(!JadeClientContract.isRunning())finish(client,null);}
            else if(phase==30){ComputerSoundClientContract.tick();if(!ComputerSoundClientContract.isRunning())finish(client,null);}
        }catch(Throwable error){failed(client,error);}
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static List<?> lookup(IJeiRuntime runtime,RecipeType type,List focuses){return runtime.getRecipeManager().createRecipeLookup(type).limitFocus(focuses).get().toList();}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void show(IJeiRuntime runtime,RecipeType type,List pages,List focuses){IRecipeCategory category=runtime.getRecipeManager().getRecipeCategory(type);require(category!=null,"Actual category is absent "+type);runtime.getRecipesGui().showRecipes(category,pages,focuses);}
    private static String factory(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("oc:lootFactory");}
    private static boolean worldDiskVisible(IJeiRuntime runtime){return runtime.getIngredientManager().getAllItemStacks().stream().anyMatch(stack->factory(stack).equals("opencomputers:openos")&&stack.getHoverName().getString().equals("JEI world OpenOS"));}
    private static void verifyCatalog(IJeiRuntime runtime){
        var ingredients=runtime.getIngredientManager();
        for(String name:List.of("robot","drone","tablet","microcontroller","print","floppycontainer","present"))require(ingredients.getAllItemStacks().stream().noneMatch(stack->ItemStack.isSameItemSameComponents(stack,item(name))),"JEI retained hidden empty base "+name);
        require(ingredients.getAllItemStacks().stream().anyMatch(stack->stack.is(item("robot").getItem())),"JEI lost configured robot subtypes");
        var helper=ingredients.getIngredientHelper(VanillaTypes.ITEM_STACK);ItemStack first=item("floppy"),second=first.copy();CompoundTag a=new CompoundTag();a.putString("oc:lootFactory","opencomputers:openos");first.set(DataComponents.CUSTOM_DATA,CustomData.of(a));CompoundTag b=a.copy();b.putString("oc:fs.uuid",UUID.randomUUID().toString());second.set(DataComponents.CUSTOM_DATA,CustomData.of(b));
        require(helper.getUniqueId(first,UidContext.Ingredient).equals(helper.getUniqueId(second,UidContext.Ingredient)),"Mutable disk UUID created another JEI subtype");b.putString("oc:lootFactory","opencomputers:plan9k");second.set(DataComponents.CUSTOM_DATA,CustomData.of(b));require(!helper.getUniqueId(first,UidContext.Ingredient).equals(helper.getUniqueId(second,UidContext.Ingredient)),"Different disk factories collapsed into one JEI subtype");
        results.add("PASS I10.catalog actual JEI runtime retains configured devices, removes empty base stacks and identifies floppy subtypes by factory rather than filesystem UUID");
    }
    private static void verifyClientWorld(Minecraft client)throws Exception{
        var convert=Class.forName("li.cil.oc.server.driver.Registry").getMethod("convert",Object[].class,net.minecraft.core.HolderLookup.Provider.class);
        Object[] values=(Object[])convert.invoke(null,new Object[]{client.level},client.level.registryAccess());
        require(values.length==1&&values[0] instanceof Map<?,?>,"Actual client Level did not convert");Map<?,?> value=(Map<?,?>)values[0];
        byte[] bytes=java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(0).putInt(0).array();
        String expected=UUID.nameUUIDFromBytes(java.security.MessageDigest.getInstance("MD5").digest(bytes)).toString();
        require(value.keySet().equals(Set.of("id","name"))&&"overworld".equals(value.get("name"))&&expected.equals(value.get("id")),"Actual client world did not preserve seed-zero original metadata: "+value);
        results.add("PASS I12.client-level real ClientLevel passes the registered World/provider conversion with original seed-zero double-MD5 identity and only id/name fields");
    }
    private static void rendered(RenderFrameEvent.Post event){
        frame++;if(finished||frame-phaseFrame<4)return;Minecraft client=Minecraft.getInstance();
        try{
            switch(phase){
                case 2->{screenshot(client,"jei-api-first.png");require(nextPage(client),"Long callback category has no next page");advance(3);}
                case 3->{require(!Objects.equals(priorPage,field(client.screen,"pageString")),"Actual JEI page button did not navigate");if(nextPage(client)){phaseFrame=frame;return;}screenshot(client,"jei-api-last.png");results.add("PASS I10.api actual JEI next-page buttons navigate and render the original GPU callback pages; pages="+pages.size());var runtime=ClientJeiProbe.runtime;var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT,VanillaTypes.ITEM_STACK,runtime.getIngredientManager().getAllItemStacks().stream().filter(stack->stack.is(item("tablet").getItem())).findFirst().orElseThrow());var manual=lookup(runtime,manuals,List.of(focus));require(!manual.isEmpty(),"JEI has no actual tablet manual usage");show(runtime,manuals,List.of(manual.getFirst()),List.of(focus));advance(4);}
                case 4->{screenshot(client,"jei-manual-button.png");IRecipeLayoutDrawable<?> layout=visibleLayout(client.screen);var rect=layout.getRect();cursor(client,rect.getX()+80,rect.getY()+20);advance(5);}
                case 5->{IRecipeLayoutDrawable<?> layout=visibleLayout(client.screen);var rect=layout.getRect();Screen screen=client.screen;require(screen.mouseClicked(rect.getX()+80,rect.getY()+20,0),"Actual JEI manual button rejected its observed coordinates");screen.mouseReleased(rect.getX()+80,rect.getY()+20,0);require(client.screen!=null&&client.screen.getClass().getName().equals("li.cil.oc.client.gui.ManualScreen"),"JEI manual button did not open the actual manual");Object current=API.manual.getClass().getMethod("current").invoke(API.manual);require(Manual.pathFor(item("tablet")).equals(current.getClass().getMethod("path").invoke(current)),"JEI usage opened the wrong real manual page");advance(6);}
                case 6->{screenshot(client,"jei-tablet-manual.png");results.add("PASS I10.manual actual JEI usage button opens the original tablet page in the real player manual");client.screen.onClose();casePos=client.player.blockPosition().east(2);var server=client.getSingleplayerServer();work=CompletableFuture.runAsync(()->require(server.overworld().setBlock(casePos,Items.get("case3").block().defaultBlockState(),3),"Real Case placement failed"),server);advance(7);}
                case 9->{require(ClientJeiProbe.runtime.getIngredientListOverlay().isListDisplayed(),"Actual JEI item list is hidden");require(ClientJeiProbe.runtime.getIngredientListOverlay().getVisibleIngredients(VanillaTypes.ITEM_STACK).stream().anyMatch(stack->stack.is(item("cpu1").getItem())),"Actual JEI filter found no tier-one CPU");GLFW.glfwFocusWindow(client.getWindow().getWindow());cursor(client,2,2);advance(10);}
                case 10->{beforeHover=Screenshot.takeScreenshot(client.getMainRenderTarget());scan=0;locateCpu(client);advance(11);}
                case 11->{ItemStack hovered=ClientJeiProbe.runtime.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);if(hovered!=null&&hovered.is(item("cpu1").getItem()))advance(12);else{require(++scan<=20,"Native hover did not reach the actual JEI CPU; active="+client.isWindowActive()+", grabbed="+client.mouseHandler.isMouseGrabbed()+", mouse="+client.mouseHandler.xpos()+","+client.mouseHandler.ypos()+", requested="+hoverX+","+hoverY+", scale="+client.getWindow().getGuiScale()+", screen="+sockets.width+"x"+sockets.height+", window="+client.getWindow().getScreenWidth()+"x"+client.getWindow().getScreenHeight()+", hovered="+hovered);cursor(client,hoverX+(scan%2==0?1:-1),hoverY);phaseFrame=frame;}}
                case 12->{try(NativeImage after=Screenshot.takeScreenshot(client.getMainRenderTarget())){verifyHighlight(client,after);after.writeToFile(directory.resolve("jei-sockets-highlight.png"));}beforeHover.close();beforeHover=null;results.add("PASS I10.highlight actual hovered JEI CPU brightens compatible hardware sockets in the real Case screen while the player inventory stays unchanged");client.player.closeContainer();ClientJeiProbe.runtime.getIngredientFilter().setFilterText("");var server=client.getSingleplayerServer();work=CompletableFuture.runAsync(()->{try{var type=Class.forName("li.cil.oc.common.integration.LootIntegration$DiskSync");var payload=(CustomPacketPayload)type.getConstructor(List.class).newInstance(List.of());PacketDistributor.sendToPlayer(server.getPlayerList().getPlayer(client.player.getUUID()),payload);}catch(Exception error){throw new IllegalStateException(error);}},server);advance(14);}
                default->{}
            }
        }catch(Throwable error){failed(client,error);}
    }
    private static Object field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static boolean nextPage(Minecraft client)throws Exception{
        String label=String.valueOf(field(client.screen,"pageString"));var numbers=java.util.regex.Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(label);require(numbers.find(),"Unrecognized actual JEI page label "+label);
        if(Integer.parseInt(numbers.group(1))==Integer.parseInt(numbers.group(2)))return false;
        var button=(net.minecraft.client.gui.components.AbstractWidget)field(client.screen,"nextPage");priorPage=label;
        require(button.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+button.getHeight()/2D,0),"Actual JEI next-page widget rejected its coordinates");button.mouseReleased(button.getX()+1,button.getY()+1,0);pageClicks++;return true;
    }
    private static IRecipeLayoutDrawable<?> visibleLayout(Screen screen)throws Exception{require(screen!=null&&screen.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui"),"Actual JEI RecipesGui not open");Object layouts=field(screen,"layouts");var rows=(List<?>)field(layouts,"recipeLayoutsWithButtons");require(!rows.isEmpty(),"Actual JEI screen has no rendered recipe layout");return(IRecipeLayoutDrawable<?>)rows.getFirst().getClass().getMethod("recipeLayout").invoke(rows.getFirst());}
    private static void cursor(Minecraft client,int x,int y)throws Exception{
        hoverX=x;hoverY=y;double scale=client.getWindow().getGuiScale();
        // A GLFW warp did not deliver cursor events on this Windows driver. Dispatch the
        // real vanilla cursor callback, as the other stages dispatch real widget input.
        var callback=client.mouseHandler.getClass().getDeclaredMethod("onMove",long.class,double.class,double.class);callback.setAccessible(true);
        callback.invoke(client.mouseHandler,client.getWindow().getWindow(),x*scale,y*scale);
    }
    private static void locateCpu(Minecraft client)throws Exception{
        Object contents=field(ClientJeiProbe.runtime.getIngredientListOverlay(),"contents");
        try(var slots=(java.util.stream.Stream<?>)contents.getClass().getMethod("getSlots").invoke(contents)){
            for(Object slot:slots.toList()){
                var candidate=(Optional<?>)slot.getClass().getMethod("getClickableIngredient").invoke(slot);if(candidate.isEmpty())continue;
                Object clickable=candidate.get();var method=clickable.getClass().getMethod("getTypedIngredient");method.setAccessible(true);
                var typed=(mezz.jei.api.ingredients.ITypedIngredient<?>)method.invoke(clickable);var stack=typed.getItemStack();if(stack.isEmpty()||!stack.get().is(item("cpu1").getItem()))continue;
                Object area=slot.getClass().getMethod("getArea").invoke(slot);Class<?> type=area.getClass();int x=(int)type.getMethod("getX").invoke(area),y=(int)type.getMethod("getY").invoke(area),w=(int)type.getMethod("getWidth").invoke(area),h=(int)type.getMethod("getHeight").invoke(area);cursor(client,x+w/2,y+h/2);return;
            }
        }
        throw new AssertionError("Actual visible JEI slots contain no CPU despite the filtered catalog");
    }
    private static void verifyHighlight(Minecraft client,NativeImage after){
        double scale=client.getWindow().getGuiScale();var slot=sockets.getMenu().slots.stream().filter(s->!(s.container instanceof net.minecraft.world.entity.player.Inventory)&&s.mayPlace(item("cpu1"))).findFirst().orElseThrow();
        for(int dx:new int[]{3,8,12})for(int dy:new int[]{3,8,12}){int x=(int)((sockets.getGuiLeft()+slot.x+dx)*scale),y=(int)((sockets.getGuiTop()+slot.y+dy)*scale);int before=beforeHover.getPixelRGBA(x,y),now=after.getPixelRGBA(x,y);for(int shift:new int[]{0,8,16}){int expected=((before>>>shift&255)*127+255*128)/255;require(Math.abs((now>>>shift&255)-expected)<=3,"Actual compatible socket alpha overlay mismatch at "+x+","+y+" before="+Integer.toHexString(before)+" after="+Integer.toHexString(now));}}
        var player=sockets.getMenu().slots.stream().filter(s->s.container instanceof net.minecraft.world.entity.player.Inventory).reduce((a,b)->b).orElseThrow();int x=(int)((sockets.getGuiLeft()+player.x+8)*scale),y=(int)((sockets.getGuiTop()+player.y+8)*scale);require(beforeHover.getPixelRGBA(x,y)==after.getPixelRGBA(x,y),"JEI hover highlighted an unrelated player socket");
    }
    private static void screenshot(Minecraft client,String name)throws Exception{try(NativeImage image=Screenshot.takeScreenshot(client.getMainRenderTarget())){image.writeToFile(directory.resolve(name));}results.add("SCREENSHOT "+name+" actual frame="+frame);}
    private static void failed(Minecraft client,Throwable error){
        if(phase>=2&&phase<20&&client.player!=null&&client.level!=null){
            results.add("FAIL I10.client phase="+phase+": "+error);LOG.error("JEI acceptance failed; continuing independent Jade acceptance",error);
            try{screenshot(client,"jei-failure.png");if(beforeHover!=null){beforeHover.close();beforeHover=null;}client.player.closeContainer();client.setScreen(null);JadeClientContract.start(client,directory,results);advance(20);return;}
            catch(Throwable jade){finish(client,jade);return;}
        }
        finish(client,error);
    }
    private static void finish(Minecraft client,Throwable error){
        if(finished)return;finished=true;if(beforeHover!=null)beforeHover.close();
        if(error!=null){results.add("FAIL client.integrations phase="+phase+": "+error);LOG.error("CLIENT_INTEGRATION failed phase={}",phase,error);try{screenshot(client,"client-failure.png");}catch(Exception capture){LOG.warn("Capturing failure frame",capture);}}
        boolean passed=error==null&&results.stream().noneMatch(value->value.startsWith("FAIL"));
        var lines=new ArrayList<>(List.of("status="+(passed?"PASS":"FAIL"),"scenario=client-integrations","run_id="+runId));lines.addAll(results);
        try{Files.write(report,lines,StandardCharsets.UTF_8);for(String line:lines)LOG.info("CLIENT_INTEGRATION {}",line);}catch(Exception failure){LOG.error("Writing client report",failure);}finally{client.stop();}
    }
}
