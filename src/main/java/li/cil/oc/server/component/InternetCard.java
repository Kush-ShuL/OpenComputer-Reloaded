package li.cil.oc.server.component;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.prefab.AbstractValue;
import li.cil.oc.util.InternetFilteringRule;

/** Original Internet component with asynchronous HTTP and nonblocking TCP userdata. */
public final class InternetCard extends AbstractManagedEnvironment implements DeviceInfo {
    private static final AtomicInteger THREAD_ID=new AtomicInteger();
    private static ThreadFactory threadFactory(String name) { return task->li.cil.oc.util.ThreadPoolFactory.daemon(task,"OpenComputers-"+name+"-"+THREAD_ID.incrementAndGet()); }
    private static final class Workers {
        static final ExecutorService IO=Executors.newFixedThreadPool(Math.max(1,Settings.get().internetThreads),threadFactory("Internet"));
        // Closing a blocking HTTP stream must also stay off the server thread.
        static final ExecutorService CLOSE=Executors.newCachedThreadPool(threadFactory("Internet-close"));
    }
    private volatile Context owner;
    private final EnvironmentHost host;
    private final Set<Closeable> connections=ConcurrentHashMap.newKeySet();
    public InternetCard() { this(null); }
    public InternetCard(EnvironmentHost host) { this.host=host;setNode(Network.newNode(this,Visibility.Network).withComponent("internet",Visibility.Neighbors).create()); }
    @Override public Map<String,String> getDeviceInfo() { return Map.of("class","communication","description","Internet modem","vendor","MightyPirates GmbH & Co. KG","product","SuperLink X-D4NK"); }
    @Callback(direct=true,doc="function():boolean -- Whether HTTP requests are enabled in the configuration.")
    public Object[] isHttpEnabled(Context context,Arguments args) { return new Object[]{Settings.get().httpEnabled}; }
    @Callback(direct=true,doc="function():boolean -- Whether TCP connections are enabled in the configuration.")
    public Object[] isTcpEnabled(Context context,Arguments args) { return new Object[]{Settings.get().tcpEnabled}; }
    private void checkOwner(Context context) {
        Context current=owner;
        if(current==null||context.node()!=current.node())throw new IllegalArgumentException("can only be used by the owning computer");
    }
    private boolean available() { return InternetFilteringRule.policy().available(); }
    private void checkLimit() throws IOException { if(connections.size()>=Settings.get().maxConnections)throw new IOException("too many open connections"); }
    @Callback(doc="function(url:string[,postData:string[,headers:table[,method:string]]]):userdata -- Start an asynchronous HTTP request.")
    public synchronized Object[] request(Context context,Arguments args) throws IOException {
        checkOwner(context);String address=args.checkString(0);
        if(!available())return new Object[]{null,"internet access is unavailable"};
        if(!Settings.get().httpEnabled)return new Object[]{null,"http requests are unavailable"};checkLimit();
        String post=args.isString(1)?args.checkString(1):null,method=args.isString(3)?args.checkString(3):null;
        Map<String,String> headers=new LinkedHashMap<>();
        if(args.isTable(2))for(Object entryObject:args.checkTable(2).entrySet()) {
            var entry=(Map.Entry<?,?>)entryObject;if(entry.getKey() instanceof String key)headers.put(key,String.valueOf(entry.getValue()));
        }
        if(!Settings.get().httpHeadersEnabled&&!headers.isEmpty())return new Object[]{null,"http request headers are unavailable"};
        Proxy proxy=host!=null&&host.world()!=null&&host.world().getServer()!=null?host.world().getServer().getProxy():Proxy.NO_PROXY;
        var request=new HTTPRequest(this,checkAddress(address),post,headers,method,proxy);connections.add(request);return new Object[]{request};
    }
    @Callback(doc="function(address:string[,port:number]):userdata -- Open an asynchronous TCP socket.")
    public synchronized Object[] connect(Context context,Arguments args) throws IOException {
        checkOwner(context);String address=args.checkString(0);int port=args.optInteger(1,-1);
        if(!available())return new Object[]{null,"internet access is unavailable"};
        if(!Settings.get().tcpEnabled)return new Object[]{null,"tcp connections are unavailable"};checkLimit();
        var socket=new TCPSocket(this,checkUri(address,port),port);connections.add(socket);return new Object[]{socket};
    }
    @Override public synchronized void onConnect(Node node) {
        if(owner==null&&node.host() instanceof Context context&&node.isNeighborOf(node()))owner=context;
        if(owner==null&&node==node())for(Node neighbor:node().neighbors())if(neighbor.host() instanceof Context context){owner=context;break;}
    }
    private void closeConnections() { for(Closeable connection:List.copyOf(connections))try{connection.close();}catch(IOException ignored){}connections.clear(); }
    @Override public synchronized void onDisconnect(Node node) {
        if(node==node()||owner!=null&&node.host()==owner){owner=null;closeConnections();}
    }
    @Override public synchronized void onMessage(Message message) {
        if(owner!=null&&message.data().length==0&&(message.name().equals("computer.stopped")||message.name().equals("computer.started"))&&Objects.equals(message.source().address(),owner.node().address()))closeConnections();
    }
    private static URL checkAddress(String address) throws IOException {
        URL url;try{url=URI.create(address).toURL();}catch(IllegalArgumentException|MalformedURLException error){throw new FileNotFoundException("invalid address");}
        if(!url.getProtocol().equalsIgnoreCase("http")&&!url.getProtocol().equalsIgnoreCase("https"))throw new FileNotFoundException("unsupported protocol");return url;
    }
    private static URI checkUri(String address,int port) {
        try{URI parsed=new URI(address);if(parsed.getHost()!=null&&(parsed.getPort()>0||port>0))return parsed;}catch(URISyntaxException ignored){}
        try{URI simple=new URI("oc://"+address);if(simple.getHost()!=null){if(simple.getPort()>0)return simple;if(port>0)return new URI("oc",null,simple.getHost(),port,null,null,null);}}catch(URISyntaxException ignored){}
        throw new IllegalArgumentException("address could not be parsed or no valid port given");
    }
    public static boolean isRequestAllowed(Settings settings,InetAddress address,String host) throws IOException {
        return InternetFilteringRule.policy(settings).allowed(address,host);
    }
    public static void checkLists(InetAddress address,String host) throws IOException {
        if(!isRequestAllowed(Settings.get(),address,host))throw new FileNotFoundException("address is not allowed");
    }
    private static IOException failure(Throwable error) {
        while(error instanceof ExecutionException||error instanceof CompletionException){if(error.getCause()==null)break;error=error.getCause();}
        if(error instanceof UnknownHostException)return new IOException("unknown host: "+Objects.toString(error.getMessage(),error.toString()),error);
        return error instanceof IOException io?io:new IOException(Objects.toString(error.getMessage(),error.toString()),error);
    }
    private static int readSize(Arguments args) { return Math.min(Settings.get().maxReadBuffer,Math.max(0,args.optInteger(0,Integer.MAX_VALUE))); }

    /** One-shot readability notifications, rearmed after each read/finishConnect. */
    private static final class TCPNotifier {
        private record Registration(SocketChannel channel,Runnable ready) {}
        private static final ConcurrentLinkedQueue<Registration> PENDING=new ConcurrentLinkedQueue<>();
        private static final Selector SELECTOR;
        static {
            try{SELECTOR=Selector.open();}catch(IOException e){throw new ExceptionInInitializerError(e);}
            threadFactory("Internet-TCPNotifier").newThread(TCPNotifier::run).start();
        }
        static void add(SocketChannel channel,Runnable ready){PENDING.add(new Registration(channel,ready));SELECTOR.wakeup();}
        private static void run() {
            while(true)try{
                SELECTOR.select();
                for(var iterator=SELECTOR.selectedKeys().iterator();iterator.hasNext();) {
                    SelectionKey key=iterator.next();iterator.remove();
                    if(key.isValid()&&key.isReadable()){Runnable ready=(Runnable)key.attachment();key.cancel();try{ready.run();}catch(RuntimeException error){OpenComputers.LOGGER.debug("Internet readiness notification failed",error);}}
                }
                // Purge cancelled keys before a channel is rearmed.
                SELECTOR.selectNow();
                Registration registration;
                while((registration=PENDING.poll())!=null)try{
                    if(registration.channel.isOpen()&&registration.channel.isConnected())registration.channel.register(SELECTOR,SelectionKey.OP_READ,registration.ready);
                }catch(ClosedChannelException|CancelledKeyException ignored){}
            }catch(CancelledKeyException ignored){}catch(IOException error){OpenComputers.LOGGER.warn("Internet socket selector failed",error);}
        }
    }

    public static final class TCPSocket extends AbstractValue implements Closeable {
        private volatile InternetCard owner;
        private SocketChannel channel;
        private Future<?> address;
        private final UUID id=UUID.randomUUID();
        public TCPSocket() {} // A persisted live OS socket intentionally restores closed.
        private TCPSocket(InternetCard owner,URI uri,int port) throws IOException {
            this.owner=owner;channel=SocketChannel.open();channel.configureBlocking(false);
            SocketChannel connecting=channel;
            address=Workers.IO.submit(()->{
                try{InetAddress resolved=InetAddress.getByName(uri.getHost());checkLists(resolved,uri.getHost());connecting.connect(new InetSocketAddress(resolved,uri.getPort()!=-1?uri.getPort():port));}
                catch(IOException error){throw new CompletionException(error);}
            });
        }
        private boolean connected() throws IOException {
            if(owner==null)throw new IOException("connection lost");
            try {
                if(!address.isDone())return false;address.get();return channel.isConnected()||channel.finishConnect();
            }catch(InterruptedException error){Thread.currentThread().interrupt();close();throw new IOException("connection interrupted",error);}
            catch(ExecutionException|CancellationException|IOException error){close();throw failure(error);}
        }
        private void notifyReadable() {
            SocketChannel current=channel;if(current==null||!current.isConnected())return;
            TCPNotifier.add(current,()->{InternetCard card=owner;if(card!=null)card.node().sendToVisible("computer.signal","internet_ready",id.toString());});
        }
        @Callback(doc="function():boolean -- Poll whether the TCP connection has completed.")
        public synchronized Object[] finishConnect(Context context,Arguments args) throws IOException { boolean connected=connected();if(connected)notifyReadable();return new Object[]{connected}; }
        @Callback(doc="function([n:number]):string -- Read available bytes, an empty string while waiting, or nil at EOF.")
        public synchronized Object[] read(Context context,Arguments args) throws IOException {
            int n=readSize(args);if(!connected())return new Object[]{new byte[0]};
            var buffer=ByteBuffer.allocate(n);int read=channel.read(buffer);if(read<0)return new Object[]{null};notifyReadable();return new Object[]{Arrays.copyOf(buffer.array(),read)};
        }
        @Callback(doc="function(data:string):number -- Write available bytes and return the count written.")
        public synchronized Object[] write(Context context,Arguments args) throws IOException { return new Object[]{connected()?channel.write(ByteBuffer.wrap(args.checkByteArray(0))):0}; }
        @Callback(direct=true,doc="function():string -- The connection's readiness signal ID.")
        public Object[] id(Context context,Arguments args) { return new Object[]{id.toString()}; }
        @Callback(direct=true,doc="function() -- Close this socket.")
        public Object[] close(Context context,Arguments args) { close();return null; }
        @Override public void dispose(Context context) { close(); }
        @Override public synchronized void close() {
            InternetCard card=owner;owner=null;if(card!=null)card.connections.remove(this);
            if(address!=null){address.cancel(true);address=null;}
            if(channel!=null){try{channel.close();}catch(IOException ignored){}channel=null;}
        }
    }

    public static final class HTTPRequest extends AbstractValue implements Closeable {
        private record Response(int code,String message,Map<String,List<String>> headers) {}
        private volatile InternetCard owner;
        private Future<InputStream> stream;
        private Future<?> reader;
        private volatile InputStream input;
        private volatile HttpURLConnection connection;
        private volatile Response response;
        private final ConcurrentLinkedQueue<Byte> queue=new ConcurrentLinkedQueue<>();
        private volatile boolean eof;
        private volatile IOException readFailure;
        public HTTPRequest() {} // Restored HTTP userdata cannot revive a network connection.
        private HTTPRequest(InternetCard owner,URL url,String post,Map<String,String> headers,String method,Proxy proxy) {
            this.owner=owner;stream=Workers.IO.submit(()->open(url,post,headers,method,proxy));
        }
        private InputStream open(URL initial,String post,Map<String,String> headers,String method,Proxy proxy) throws IOException {
            try {
                URL url=initial;String requestMethod=method!=null?method:post!=null?"POST":"GET";
                int timeout=Math.max(0,Settings.get().config.getInt("internet.requestTimeout"));
                int timeoutMillis=(int)Math.min(Integer.MAX_VALUE,timeout*1000L);
                for(int redirects=0;redirects<=20;redirects++) {
                    if(owner==null||Thread.currentThread().isInterrupted())throw new IOException("connection lost");
                    // Redirect targets are checked independently, including redirects to private networks.
                    InetAddress[] resolved=InetAddress.getAllByName(url.getHost());for(InetAddress address:resolved)checkLists(address,url.getHost());
                    if(owner==null||Thread.currentThread().isInterrupted())throw new IOException("connection lost");
                    HttpURLConnection http=(HttpURLConnection)url.openConnection(proxy==null?Proxy.NO_PROXY:proxy);connection=http;
                    if(owner==null){http.disconnect();throw new IOException("connection lost");}
                    http.setInstanceFollowRedirects(false);http.setConnectTimeout(timeoutMillis);http.setReadTimeout(timeoutMillis);
                    http.setDoInput(true);http.setDoOutput(post!=null);http.setRequestMethod(requestMethod);
                    http.setRequestProperty("User-Agent",Settings.get().httpUserAgent.replace("$version",OpenComputers.VERSION));
                    headers.forEach(http::setRequestProperty);
                    if(post!=null)try(var output=new BufferedWriter(new OutputStreamWriter(http.getOutputStream(),StandardCharsets.UTF_8))){output.write(post);}
                    int code=http.getResponseCode();String location=http.getHeaderField("Location");
                    if(location!=null&&(code==301||code==302||code==303||code==307||code==308)) {
                        URL next=checkAddress(new URL(url,location).toString());http.disconnect();url=next;
                        if(code==303||(code==301||code==302)&&requestMethod.equals("POST")){requestMethod="GET";post=null;}
                        continue;
                    }
                    response=new Response(code,http.getResponseMessage(),http.getHeaderFields());
                    // Match the original: unsuccessful responses expose metadata, but read throws.
                    InputStream result=http.getInputStream();input=result;
                    if(owner==null){result.close();http.disconnect();throw new IOException("connection lost");}
                    return result;
                }
                throw new IOException("too many HTTP redirects");
            }catch(Exception error){HttpURLConnection http=connection;if(http!=null)http.disconnect();throw failure(error);}
        }
        private boolean connected() throws IOException {
            if(owner==null)throw new IOException("connection lost");
            if(!stream.isDone())return false;
            try{stream.get();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("connection interrupted",error);}
            catch(ExecutionException|CancellationException error){throw failure(error);}
            if(readFailure!=null)throw readFailure;
            if(reader==null)readMore();return true;
        }
        private void readMore() {
            if(eof||owner==null||reader!=null&&!reader.isDone())return;
            InputStream current=input;
            reader=Workers.IO.submit(()->{
                try {
                    byte[] buffer=new byte[Settings.get().maxReadBuffer];int count=current.read(buffer);
                    if(count<0)eof=true;else for(int i=0;i<count;i++)queue.add(buffer[i]);
                }catch(IOException error){readFailure=error;}
            });
        }
        @Callback(doc="function():boolean -- Poll whether HTTP response headers are available.")
        public synchronized Object[] finishConnect(Context context,Arguments args) throws IOException { return new Object[]{connected()}; }
        @Callback(direct=true,doc="function():number,string,table -- Response status, reason and header values.")
        public Object[] response(Context context,Arguments args) { Response value=response;return value==null?new Object[]{null}:new Object[]{value.code,value.message,value.headers}; }
        @Callback(doc="function([n:number]):string -- Read available response bytes, an empty string while waiting, or nil at EOF.")
        public synchronized Object[] read(Context context,Arguments args) throws IOException {
            int n=readSize(args);if(!connected())return new Object[]{new byte[0]};if(eof&&queue.isEmpty())return new Object[]{null};
            var buffer=new ByteArrayOutputStream(Math.min(n,queue.size()));while(buffer.size()<n){Byte value=queue.poll();if(value==null)break;buffer.write(value);}
            if(buffer.size()==0)readMore();return new Object[]{buffer.toByteArray()};
        }
        @Callback(direct=true,doc="function() -- Close this HTTP response stream.")
        public Object[] close(Context context,Arguments args) { close();return null; }
        @Override public void dispose(Context context) { close(); }
        @Override public synchronized void close() {
            InternetCard card=owner;owner=null;if(card!=null)card.connections.remove(this);
            if(stream!=null){stream.cancel(true);stream=null;}if(reader!=null){reader.cancel(true);reader=null;}
            InputStream closing=input;HttpURLConnection disconnecting=connection;input=null;connection=null;queue.clear();
            if(closing!=null||disconnecting!=null)Workers.CLOSE.submit(()->{if(disconnecting!=null)disconnecting.disconnect();try{if(closing!=null)closing.close();}catch(IOException ignored){}});
        }
    }
}
