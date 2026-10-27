package li.cil.oc.util;

import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;

/** Ordered original allow/deny rules. All DNS work runs in the Internet worker. */
public final class InternetFilteringRule {
    private static final String[] BOGON_CIDRS={"0.0.0.0/8","10.0.0.0/8","100.64.0.0/10","127.0.0.0/8","169.254.0.0/16","172.16.0.0/12","192.0.0.0/24","192.0.2.0/24","192.168.0.0/16","198.18.0.0/15","198.51.100.0/24","203.0.113.0/24","224.0.0.0/3","::/128","::1/128","::ffff:0:0/96","::/96","64:ff9b::/96","100::/64","2001:10::/28","2001:db8::/32","fc00::/7","fe80::/10","fec0::/10","ff00::/8"};
    private static final List<InetAddressRange> BOGONS=Arrays.stream(BOGON_CIDRS).map(cidr->{var parts=cidr.split("/",2);return InetAddressRange.parse(parts[0],parts[1]);}).toList();
    private static volatile Policy policy;
    private final List<BiPredicate<InetAddress,String>> predicates=new ArrayList<>();
    private final List<Domain> domains=new ArrayList<>();
    private boolean allow,ignored;
    private volatile boolean invalid;
    private static boolean privateAddress(InetAddress address) { return address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress(); }
    private static boolean bogon(InetAddress address) { return BOGONS.stream().anyMatch(range->range.matches(address)); }
    private static final class Domain {
        final String name;
        InetAddress[] addresses;
        Domain(String name) { if(name.isEmpty()) throw new IllegalArgumentException("empty domain");this.name=name; }
        void resolve() throws IOException { if(addresses==null) addresses=InetAddress.getAllByName(name); }
        boolean matches(InetAddress address,String host) { return name.equals(host)||Arrays.asList(addresses).contains(address); }
    }
    public InternetFilteringRule(String source) {
        try {
            String[] parts=source.split(" ");
            if(parts[0].equals("removeme")) { ignored=true;return; }
            if(!parts[0].equals("allow")&&!parts[0].equals("deny")) throw new IllegalArgumentException("expected allow or deny");
            allow=parts[0].equals("allow");
            for(int i=1;i<parts.length;i++) {
                String[] filter=parts[i].split(":",2);
                switch(filter[0]) {
                    case "all" -> {}
                    case "default" -> predicates.add((address,host)->allow&&!privateAddress(address)&&!bogon(address));
                    case "private" -> predicates.add((address,host)->privateAddress(address));
                    case "bogon" -> predicates.add((address,host)->bogon(address));
                    case "ipv4" -> predicates.add((address,host)->address instanceof Inet4Address);
                    case "ipv6" -> predicates.add((address,host)->address instanceof Inet6Address);
                    case "ipv4-embedded-ipv6" -> predicates.add((address,host)->address instanceof Inet6Address ipv6&&InetAddresses.hasEmbeddedIPv4ClientAddress(ipv6));
                    case "domain" -> { var domain=new Domain(filter[1]); domains.add(domain);predicates.add(domain::matches); }
                    case "ip" -> {
                        String[] ip=filter[1].split("/",2);
                        if(ip.length==2) { var range=InetAddressRange.parse(ip[0],ip[1]);predicates.add((address,host)->range.matches(address)); }
                        else { var literal=InetAddresses.forString(ip[0]);predicates.add((address,host)->literal.equals(address)); }
                    }
                    default -> throw new IllegalArgumentException("unknown Internet filter: "+filter[0]);
                }
            }
        } catch(RuntimeException error) {
            invalid=true;OpenComputers.LOGGER.error("Invalid Internet filteringRules rule in configuration: \"{}\".",source,error);
        }
    }
    public boolean invalid() { return invalid; }
    public Boolean apply(InetAddress address,String host) {
        if(invalid) return false;
        if(ignored) return null;
        try{resolve();}catch(IOException error){OpenComputers.LOGGER.error("Invalid Internet filtering rule domain",error);return false;}
        return predicates.stream().allMatch(predicate->predicate.test(address,host))?allow:null;
    }
    private void resolve() throws IOException { try{for(var domain:domains) domain.resolve();}catch(IOException error){invalid=true;throw error;} }
    public static Policy policy() { return policy(Settings.get()); }
    public static Policy policy(Settings settings) {
        Policy result=policy;
        if(result==null||result.settings!=settings) synchronized(InternetFilteringRule.class) {
            if(policy==null||policy.settings!=settings) policy=new Policy(settings); result=policy;
        }
        return result;
    }
    public static final class Policy {
        private final Settings settings;
        private final List<InternetFilteringRule> rules=new ArrayList<>();
        private volatile boolean invalid;
        private boolean resolved;
        private Policy(Settings settings) {
            this.settings=settings;
            // Preserve the original migration order for old whitelist/blacklist configurations.
            for(String key:List.of("blacklist","whitelist")) if(settings.config.hasPath("internet."+key)) {
                var entries=settings.config.getStringList("internet."+key);
                for(String entry:entries) {
                    boolean literal=InetAddresses.isInetAddress(entry.split("/",2)[0]);
                    rules.add(new InternetFilteringRule((key.equals("whitelist")?"allow ":"deny ")+(literal?"ip:":"domain:")+entry));
                }
                if(key.equals("whitelist")&&!entries.isEmpty()) rules.add(new InternetFilteringRule("deny all"));
            }
            rules.addAll(Arrays.asList(settings.internetFilteringRules));
            invalid=rules.stream().anyMatch(InternetFilteringRule::invalid);
        }
        public boolean available() { return settings.internetAccessAllowed()&&!invalid; }
        private synchronized void resolve() throws IOException {
            if(resolved) return;
            try { for(var rule:rules) rule.resolve(); resolved=true; }
            catch(IOException failure) { invalid=true;throw new IOException("invalid Internet filtering rule domain",failure); }
        }
        private boolean evaluate(InetAddress address,String host,boolean defaultValue) {
            for(var rule:rules) { Boolean result=rule.apply(address,host);if(result!=null)return result; } return defaultValue;
        }
        public boolean allowed(InetAddress address,String host) throws IOException {
            resolve(); if(!available())return false;
            if(address instanceof Inet6Address ipv6) {
                if(InetAddresses.hasEmbeddedIPv4ClientAddress(ipv6)&&!evaluate(InetAddresses.getEmbeddedIPv4ClientAddress(ipv6),host,true))return false;
                byte[] bytes=address.getAddress();
                if(bytes[0]==0&&bytes[1]==0x64&&bytes[2]==(byte)0xff&&bytes[3]==(byte)0x9b) {
                    boolean nat64=true;for(int i=4;i<12;i++)nat64&=bytes[i]==0;
                    if(nat64&&!evaluate(InetAddress.getByAddress(Arrays.copyOfRange(bytes,12,16)),host,true))return false;
                }
            } else if(!(address instanceof Inet4Address))return false;
            return evaluate(address,host,false);
        }
    }
}
