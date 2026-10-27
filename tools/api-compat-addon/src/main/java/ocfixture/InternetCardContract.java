package ocfixture;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import li.cil.oc.api.API;
import li.cil.oc.api.Network;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real mounted card and real loopback endpoints; no firmware, Context or host replacement. */
public final class InternetCardContract {
    private static final Logger LOGGER=LoggerFactory.getLogger("oc-internet-card");
    private static final byte[] HTTP_BODY={0,1,127,(byte)128,(byte)254,(byte)255};
    private static final byte[] GREETING="actual-ready".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TCP_BODY={0,(byte)255,42,(byte)128,1,127};
    private static Run active;
    private InternetCardContract(){}
    public static void start(ServerLevel level,BlockPos position,List<String> results){
        Run run=new Run(level,position.immutable(),results);active=run;
        try{run.start();}catch(Throwable error){run.fail(error);active=null;run.cleanup();}
    }
    public static boolean isRunning(){return active!=null;}
    public static void tick(){
        Run run=active;if(run==null)return;
        try{if(!run.tick())return;}catch(Throwable error){run.fail(error);}
        active=null;run.cleanup();
    }
    public static void close(){Run run=active;active=null;if(run!=null)run.cleanup();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object first(Object[] result,String operation){require(result!=null&&result.length>0,operation+" returned no result");return result[0];}
    private static String text(Object value){return value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):String.valueOf(value);}
    private static ItemStack item(String name){var descriptor=li.cil.oc.api.Items.get(name);require(descriptor!=null,"Missing Internet hardware "+name);return descriptor.createItemStack(1);}
    @FunctionalInterface private interface Operation {void run()throws Exception;}
    private static void rejected(Operation operation,String expected)throws Exception{
        try{operation.run();}catch(Exception error){require(error.getMessage()!=null&&error.getMessage().contains(expected),"Expected "+expected+" but received "+error);return;}
        throw new AssertionError("Actual operation did not reject "+expected);
    }
    private record HttpInput(String method,String header,String agent,byte[] body){}
    private record Ready(Node source,String id){}
    /** A public SDK observer records messages from the actual installed card. */
    private static final class Observer extends AbstractManagedEnvironment {
        final ConcurrentLinkedQueue<Ready> ready=new ConcurrentLinkedQueue<>();
        Observer(){setNode(Network.newNode(this,Visibility.Network).withComponent("internet_contract_observer",Visibility.Network).create());Network.joinNewNetwork(node());}
        @Override public void onMessage(Message message){Object[] data=message.data();if("computer.signal".equals(message.name())&&data.length==2&&"internet_ready".equals(text(data[0])))ready.add(new Ready(message.source(),text(data[1])));}
        boolean saw(Node source,String id){return ready.stream().anyMatch(value->value.source==source&&value.id.equals(id));}
    }
    private static final class Run {
        final ServerLevel level;final BlockPos position;final List<String> results;
        final Map<BlockPos,BlockState> restored=new LinkedHashMap<>();final Set<Long> forced=new LinkedHashSet<>();
        final List<Value> handles=new ArrayList<>();final ConcurrentLinkedQueue<HttpInput> requests=new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<byte[]> received=new ConcurrentLinkedQueue<>();final ConcurrentLinkedQueue<Socket> clients=new ConcurrentLinkedQueue<>();
        final CountDownLatch releaseHold=new CountDownLatch(1);
        final ExecutorService endpoints=Executors.newCachedThreadPool(task->{Thread thread=new Thread(task,"OC-InternetFixture");thread.setDaemon(true);return thread;});
        final ByteArrayOutputStream httpBytes=new ByteArrayOutputStream(),tcpBytes=new ByteArrayOutputStream();
        Case computer,other;Component card;Observer observer;HttpServer httpServer;ServerSocket tcpServer;
        Value http,errorHttp,tcp,deniedHttp,deniedTcp;String baseUrl,tcpId;int ticks,phase,written;boolean httpConnected,zeroRead,deniedPoll,cleaned;
        Run(ServerLevel level,BlockPos position,List<String> results){this.level=level;this.position=position;this.results=results;}
        Case place(BlockPos pos){
            if(level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true))forced.add(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4));level.getChunkAt(pos);
            require(level.isEmptyBlock(pos)&&level.getBlockEntity(pos)==null,"Internet fixture cell is occupied: "+pos);restored.put(pos.immutable(),level.getBlockState(pos));
            require(level.setBlock(pos,li.cil.oc.api.Items.get("case3").block().defaultBlockState(),3)&&level.getBlockEntity(pos) instanceof Case,"Cannot place actual Internet Case");return (Case)level.getBlockEntity(pos);
        }
        void start()throws IOException{
            require(API.config!=null&&API.config.getBoolean("internet.enableHttp")&&API.config.getBoolean("internet.enableTcp")&&API.config.getBoolean("internet.enableHttpHeaders"),"Internet fixture requires isolated HTTP/TCP/header switches enabled");
            require(API.config.getInt("internet.maxTcpConnections")==4,"Internet fixture requires isolated/default four-connection limit");
            computer=place(position);other=place(position.east(3));
            InetAddress loopback=InetAddress.getByAddress(new byte[]{127,0,0,1});
            httpServer=HttpServer.create(new InetSocketAddress(loopback,0),0);httpServer.setExecutor(endpoints);
            httpServer.createContext("/",exchange->{
                try{
                    requests.add(new HttpInput(exchange.getRequestMethod(),exchange.getRequestHeaders().getFirst("X-Oc-Fixture"),exchange.getRequestHeaders().getFirst("User-Agent"),exchange.getRequestBody().readAllBytes()));
                    if(exchange.getRequestURI().getPath().equals("/hold"))try{releaseHold.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}
                    exchange.getResponseHeaders().add("X-Oc-Fixture-Response","actual-loopback");
                    int status=exchange.getRequestURI().getPath().equals("/missing")?404:200;exchange.sendResponseHeaders(status,HTTP_BODY.length);exchange.getResponseBody().write(HTTP_BODY);
                }finally{exchange.close();}
            });httpServer.start();baseUrl="http://127.0.0.1:"+httpServer.getAddress().getPort();
            tcpServer=new ServerSocket();tcpServer.bind(new InetSocketAddress(loopback,0));
            endpoints.submit(()->{
                while(!tcpServer.isClosed())try{Socket socket=tcpServer.accept();clients.add(socket);endpoints.submit(()->serve(socket));}
                catch(IOException error){if(!tcpServer.isClosed())LOGGER.error("Actual TCP fixture accept failed",error);}
            });
        }
        void serve(Socket socket){
            try(socket){
                socket.getOutputStream().write(GREETING);socket.getOutputStream().flush();
                byte[] bytes=socket.getInputStream().readNBytes(TCP_BODY.length);if(bytes.length==0)return;received.add(bytes);
                socket.getOutputStream().write(bytes);socket.getOutputStream().flush();socket.shutdownOutput();
            }catch(IOException error){if(!socket.isClosed()&&!cleaned)LOGGER.debug("Actual fixture socket closed during an interaction",error);}
            finally{clients.remove(socket);}
        }
        Component installed(){
            for(Node node:computer.node().network().nodes())if(node instanceof Component component&&"internet".equals(component.name())&&computer.componentSlot(node.address())==0)return component;
            throw new AssertionError("Actual installed card has no Internet component in its Case");
        }
        Value value(Object[] result,String operation){Object value=first(result,operation);require(value instanceof Value,operation+" did not create real userdata: "+Arrays.toString(result));Value handle=(Value)value;handles.add(handle);return handle;}
        Object[] call(Value value,String method,Object... args)throws Exception{return computer.machine().invoke(value,method,args);}
        void close(Value value)throws Exception{call(value,"close");}
        boolean connected(Value value)throws Exception{return Boolean.TRUE.equals(first(call(value,"finishConnect"),"finishConnect"));}
        byte[] read(Value value,int amount)throws Exception{
            Object data=first(call(value,"read",amount),"read");require(data==null||data instanceof byte[],"Actual Internet read did not return bytes or EOF");return (byte[])data;
        }
        void setup()throws Exception{
            ItemStack stack=item("internetcard");require(computer.canPlaceItem(0,stack),"Actual Case rejects a valid Internet card");computer.setItem(0,stack);card=installed();
            require(Boolean.TRUE.equals(first(card.invoke("isHttpEnabled",computer.machine()),"isHttpEnabled"))&&Boolean.TRUE.equals(first(card.invoke("isTcpEnabled",computer.machine()),"isTcpEnabled")),"Actual card did not expose the configured protocol switches");
            rejected(()->card.invoke("request",other.machine(),baseUrl+"/payload"),"owning computer");rejected(()->card.invoke("connect",other.machine(),"127.0.0.1",tcpServer.getLocalPort()),"owning computer");
            rejected(()->card.invoke("request",computer.machine(),"ftp://127.0.0.1/file"),"unsupported protocol");rejected(()->card.invoke("request",computer.machine(),":invalid"),"invalid address");
            rejected(()->card.invoke("connect",computer.machine(),"localhost"),"address could not be parsed");
            require(requests.isEmpty(),"Invalid/non-owner operations reached the actual HTTP endpoint");
            observer=new Observer();observer.node().connect(card);
            http=value(card.invoke("request",computer.machine(),baseUrl+"/payload","actual-body-真实",Map.of("X-Oc-Fixture","actual-header"),"PUT"),"request");
        }
        boolean header(Map<?,?> headers,String name,String expected){
            for(var entry:headers.entrySet())if(name.equalsIgnoreCase(text(entry.getKey()))){Object values=entry.getValue();if(values instanceof Object[] array)return Arrays.stream(array).anyMatch(value->expected.equals(text(value)));if(values instanceof Iterable<?> iterable)for(Object value:iterable)if(expected.equals(text(value)))return true;}
            return false;
        }
        boolean http()throws Exception{
            if(!httpConnected){if(!connected(http))return false;httpConnected=true;
                Object[] response=call(http,"response");require(response.length==3&&response[0] instanceof Number status&&status.intValue()==200&&"OK".equals(text(response[1]))&&response[2] instanceof Map<?,?> headers&&header(headers,"X-Oc-Fixture-Response","actual-loopback"),"Actual HTTP response status/reason/headers differ from the loopback server");
                require(read(http,0).length==0,"Zero-byte HTTP read did not return empty bytes");
            }
            byte[] data=read(http,2);if(data!=null){httpBytes.writeBytes(data);return false;}
            require(Arrays.equals(httpBytes.toByteArray(),HTTP_BODY),"Real HTTP binary response was changed, truncated or duplicated");
            HttpInput input=requests.stream().filter(request->"PUT".equals(request.method)).findFirst().orElseThrow();
            require("actual-header".equals(input.header)&&"actual-body-真实".equals(new String(input.body,StandardCharsets.UTF_8))&&input.agent!=null&&input.agent.startsWith("opencomputers/")&&!input.agent.contains("$version"),"Actual HTTP endpoint did not receive the method, UTF-8 body, custom header or substituted User-Agent");
            close(http);rejected(()->connected(http),"connection lost");
            results.add("PASS internet.http actual mounted card sends PUT/UTF-8/custom headers and receives real status/reason/header values plus binary bytes, zero read and EOF; explicit close rejects further use");
            errorHttp=value(card.invoke("request",computer.machine(),baseUrl+"/missing"),"request(error)");return true;
        }
        boolean errorResponse()throws Exception{
            try{if(!connected(errorHttp))return false;throw new AssertionError("Actual HTTP 404 unexpectedly opened a successful body stream");}
            catch(IOException error){
                Object[] response=call(errorHttp,"response");require(response.length==3&&response[0] instanceof Number status&&status.intValue()==404&&response[2] instanceof Map<?,?>,"Actual failed HTTP request discarded its 404 metadata");close(errorHttp);
            }
            results.add("PASS internet.http.error actual 404 keeps its response metadata while finishConnect rejects reading the unsuccessful response, as in the original API");
            tcp=value(card.invoke("connect",computer.machine(),"127.0.0.1",tcpServer.getLocalPort()),"connect");tcpId=text(first(call(tcp,"id"),"id"));require(java.util.UUID.fromString(tcpId).toString().equals(tcpId),"Actual TCP readiness ID is not a UUID");return true;
        }
        boolean tcp()throws Exception{
            if(!connected(tcp))return false;
            if(!observer.saw(card,tcpId))return false;
            if(!zeroRead){require(read(tcp,0).length==0,"Actual zero-byte TCP read returned data");zeroRead=true;}
            if(written<TCP_BODY.length){
                Object amount=first(call(tcp,"write",Arrays.copyOfRange(TCP_BODY,written,TCP_BODY.length)),"write");require(amount instanceof Number number&&number.intValue()>=0&&number.intValue()<=TCP_BODY.length-written,"Actual TCP write reported an invalid byte count");written+=((Number)amount).intValue();
            }
            byte[] data=read(tcp,3);if(data!=null){tcpBytes.writeBytes(data);return false;}
            byte[] expected=new byte[GREETING.length+TCP_BODY.length];System.arraycopy(GREETING,0,expected,0,GREETING.length);System.arraycopy(TCP_BODY,0,expected,GREETING.length,TCP_BODY.length);
            require(written==TCP_BODY.length&&Arrays.equals(tcpBytes.toByteArray(),expected)&&received.stream().anyMatch(bytes->Arrays.equals(bytes,TCP_BODY)),"Actual TCP bytes/readiness/echo or reported writes do not match the real server data");close(tcp);
            results.add("PASS internet.tcp real socket finishConnect, public internet_ready message/ID, zero read, binary write count, actual server input, bounded reads and EOF agree with the loopback endpoint");
            deniedHttp=value(card.invoke("request",computer.machine(),"http://127.0.0.2:"+httpServer.getAddress().getPort()+"/denied"),"request(filtered)");return true;
        }
        boolean filtering()throws Exception{
            try{if(!connected(deniedHttp))return false;throw new AssertionError("Actual configured address filter allowed 127.0.0.2");}
            catch(IOException error){require(error.getMessage()!=null&&error.getMessage().contains("address is not allowed"),"Filtered HTTP failed for an unrelated reason: "+error);close(deniedHttp);}
            deniedTcp=value(card.invoke("connect",computer.machine(),"127.0.0.2",tcpServer.getLocalPort()),"connect(filtered)");return true;
        }
        boolean deniedTcp()throws Exception{
            try{require(!connected(deniedTcp),"Filtered actual TCP connection unexpectedly succeeded");deniedPoll=true;return false;}
            catch(IOException error){require(deniedPoll&&error.getMessage()!=null&&error.getMessage().contains("connection lost"),"Failed TCP did not preserve the original false-then-closed polling order: "+error);}
            results.add("PASS internet.filter actual HTTP policy rejects the isolated denied IP before opening a connection; denied TCP first polls false and then exposes a closed descriptor");
            limits();return true;
        }
        void restoredClosed(Value original)throws Exception{
            CompoundTag saved=new CompoundTag();original.save(saved);Value restored=(Value)original.getClass().getConstructor().newInstance();restored.load(saved);handles.add(restored);
            rejected(()->connected(restored),"connection lost");
        }
        void limits()throws Exception{
            List<Value> pending=new ArrayList<>();pending.add(value(card.invoke("request",computer.machine(),baseUrl+"/hold"),"request(held)"));
            for(int index=1;index<4;index++)pending.add(value(card.invoke("connect",computer.machine(),"127.0.0.1:"+tcpServer.getLocalPort()),"connect(limit)"));
            rejected(()->card.invoke("request",computer.machine(),baseUrl+"/payload"),"too many open connections");rejected(()->card.invoke("connect",computer.machine(),"127.0.0.1",tcpServer.getLocalPort()),"too many open connections");
            String address=card.address();Component previous=card;ItemStack removed=computer.removeItem(0,1);require(!removed.isEmpty()&&previous.network()==null,"Physical Internet card removal did not detach the actual component");
            for(Value value:pending)rejected(()->connected(value),"connection lost");releaseHold.countDown();restoredClosed(http);restoredClosed(tcp);
            computer.setItem(0,removed);card=installed();require(card!=previous&&address.equals(card.address()),"Actual Internet item remount lost its node identity or reused the detached environment");observer.node().connect(card);
            http=value(card.invoke("request",computer.machine(),baseUrl+"/payload"),"request(remounted)");httpConnected=false;httpBytes.reset();
            results.add("PASS internet.lifecycle mixed actual HTTP/TCP handles share the four-connection limit; real Case card removal closes every handle, public saved userdata restores closed, and the remounted card retains its address with a fresh connection quota");
        }
        boolean remounted()throws Exception{
            if(!connected(http))return false;byte[] data=read(http,API.config.getInt("filesystem.maxReadBuffer"));if(data!=null){httpBytes.writeBytes(data);return false;}
            require(Arrays.equals(httpBytes.toByteArray(),HTTP_BODY),"Actual remounted Internet card could not receive a new complete response");close(http);
            results.add("PASS internet.remount actual remounted card opens and reads a fresh complete loopback response through the original real Case Context");return true;
        }
        boolean tick()throws Exception{
            require(++ticks<=400,"Internet contract exceeded 400 actual world ticks; phase="+phase);
            require(!computer.machine().isRunning()&&!other.machine().isRunning(),"Internet fixture must not boot firmware");
            if(phase==0){if(ticks<3)return false;setup();phase=1;return false;}
            if(phase==1){if(http())phase=2;return false;}
            if(phase==2){if(errorResponse())phase=3;return false;}
            if(phase==3){if(tcp())phase=4;return false;}
            if(phase==4){if(filtering())phase=5;return false;}
            if(phase==5){if(deniedTcp())phase=6;return false;}
            return remounted();
        }
        void fail(Throwable error){results.add("FAIL internet.card phase="+phase+": "+error);LOGGER.error("INTERNET_CARD failed at phase {}",phase,error);}
        void cleanup(){
            if(cleaned)return;cleaned=true;releaseHold.countDown();
            if(computer!=null){for(Value value:handles)value.dispose(computer.machine());computer.clearContent();computer.machine().stop();}
            if(observer!=null)observer.node().remove();if(other!=null)other.machine().stop();
            if(httpServer!=null)httpServer.stop(0);if(tcpServer!=null)try{tcpServer.close();}catch(IOException ignored){}
            for(Socket socket:clients)try{socket.close();}catch(IOException ignored){}clients.clear();endpoints.shutdownNow();
            var entries=new ArrayList<>(restored.entrySet());Collections.reverse(entries);for(var entry:entries)level.setBlock(entry.getKey(),entry.getValue(),3);restored.clear();
            for(long chunk:forced)level.setChunkForced(ChunkPos.getX(chunk),ChunkPos.getZ(chunk),false);forced.clear();
        }
    }
}
