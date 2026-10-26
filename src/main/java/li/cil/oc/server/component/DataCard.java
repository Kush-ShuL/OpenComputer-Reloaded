package li.cil.oc.server.component;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import li.cil.oc.Settings;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DeviceInfo;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.prefab.AbstractValue;
import net.minecraft.nbt.CompoundTag;

/** Java implementation of the original three data processor tiers. */
public abstract class DataCard extends AbstractManagedEnvironment implements DeviceInfo {
    private static final ThreadLocal<SecureRandom> RANDOM = ThreadLocal.withInitial(() -> {
        try { return SecureRandom.getInstance("SHA1PRNG"); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    });
    protected DataCard() {
        setNode(Network.newNode(this, Visibility.Neighbors).withComponent("data", Visibility.Neighbors).withConnector().create());
    }
    protected void checkCost(double cost) throws Exception {
        if (!((Connector) node()).tryChangeBuffer(-cost)) throw new Exception("not enough energy");
    }
    protected byte[] cost(Context context, Arguments args, double base, double perByte) throws Exception {
        byte[] data = args.checkByteArray(0);
        if (data.length > Settings.get().dataCardHardLimit) throw new IllegalArgumentException("data size limit exceeded");
        checkCost(base + data.length * perByte);
        if (data.length > Settings.get().dataCardSoftLimit) context.pause(Settings.get().dataCardTimeout);
        return data;
    }
    protected byte[] trivial(Context context, Arguments args) throws Exception { return cost(context,args,Settings.get().dataCardTrivial,Settings.get().dataCardTrivialByte); }
    protected byte[] simple(Context context, Arguments args) throws Exception { return cost(context,args,Settings.get().dataCardSimple,Settings.get().dataCardSimpleByte); }
    protected byte[] complex(Context context, Arguments args) throws Exception { return cost(context,args,Settings.get().dataCardComplex,Settings.get().dataCardComplexByte); }
    @Callback(direct=true,doc="function():number -- Maximum size accepted by data operations.")
    public Object[] getLimit(Context context, Arguments args) { return new Object[]{Settings.get().dataCardHardLimit}; }
    protected Map<String,String> info(String product) { return Map.of("class","processor","description","Data processor card","vendor","S.C. Ltd.","product",product); }

    public static class Tier1 extends DataCard {
        @Override public Map<String,String> getDeviceInfo() { return info("SC01D H45h3r"); }
        @Callback(direct=true,limit=32,doc="function(data:string):string -- Encode binary data as base64.")
        public Object[] encode64(Context context, Arguments args) throws Exception { return new Object[]{Base64.getEncoder().encode(trivial(context,args))}; }
        @Callback(direct=true,limit=32,doc="function(data:string):string -- Decode base64 data.")
        public Object[] decode64(Context context, Arguments args) throws Exception {
            // Commons Codec in the original accepts both alphabets and ignores non-alphabet bytes.
            byte[] input=trivial(context,args);
            StringBuilder clean=new StringBuilder(input.length);
            for (byte b:input) {
                char c=(char)(b&255);
                if(c=='=') break;
                if(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||c=='+'||c=='/') clean.append(c);
                else if(c=='-') clean.append('+'); else if(c=='_') clean.append('/');
            }
            if(clean.length()%4==1) clean.setLength(clean.length()-1);
            return new Object[]{Base64.getDecoder().decode(clean.toString().getBytes(StandardCharsets.US_ASCII))};
        }
        @Callback(direct=true,limit=4,doc="function(data:string):string -- Compress data with deflate.")
        public Object[] deflate(Context context, Arguments args) throws Exception {
            byte[] data=complex(context,args); var out=new ByteArrayOutputStream(512);
            try(var zip=new DeflaterOutputStream(out)) { zip.write(data); zip.finish(); }
            return new Object[]{out.toByteArray()};
        }
        @Callback(direct=true,limit=4,doc="function(data:string):string -- Decompress deflate data.")
        public Object[] inflate(Context context, Arguments args) throws Exception {
            byte[] data=complex(context,args); var out=new ByteArrayOutputStream(512);
            try(var zip=new InflaterOutputStream(out)) { zip.write(data); zip.finish(); }
            return new Object[]{out.toByteArray()};
        }
        @Callback(direct=true,limit=32,doc="function(data:string):string -- Compute a binary CRC-32 hash.")
        public Object[] crc32(Context context, Arguments args) throws Exception {
            var crc=new CRC32(); crc.update(trivial(context,args));
            return new Object[]{ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int)crc.getValue()).array()};
        }
        @Callback(direct=true,limit=8,doc="function(data:string):string -- Compute a binary MD5 hash.")
        public Object[] md5(Context context, Arguments args) throws Exception { return new Object[]{MessageDigest.getInstance("MD5").digest(simple(context,args))}; }
        @Callback(direct=true,limit=4,doc="function(data:string):string -- Compute a binary SHA-256 hash.")
        public Object[] sha256(Context context, Arguments args) throws Exception { return new Object[]{MessageDigest.getInstance("SHA-256").digest(complex(context,args))}; }
    }
    public static class Tier2 extends Tier1 {
        @Override public Map<String,String> getDeviceInfo() { return info("SC02D Cryptic"); }
        private Object[] hmac(byte[] data,byte[] key,String algorithm) throws Exception {
            var mac=Mac.getInstance(algorithm); mac.init(new SecretKeySpec(key,algorithm)); return new Object[]{mac.doFinal(data)};
        }
        @Override @Callback(direct=true,limit=8,doc="function(data:string[,hmacKey:string]):string -- Compute an MD5 hash or HMAC.")
        public Object[] md5(Context context,Arguments args) throws Exception {
            return args.count()>1 ? hmac(simple(context,args),args.checkByteArray(1),"HmacMD5") : super.md5(context,args);
        }
        @Override @Callback(direct=true,limit=4,doc="function(data:string[,hmacKey:string]):string -- Compute a SHA-256 hash or HMAC.")
        public Object[] sha256(Context context,Arguments args) throws Exception {
            return args.count()>1 ? hmac(complex(context,args),args.checkByteArray(1),"HmacSHA256") : super.sha256(context,args);
        }
        private Object[] crypt(Context context,Arguments args,int mode) throws Exception {
            byte[] data=simple(context,args),key=args.checkByteArray(1);
            if(key.length!=16) throw new IllegalArgumentException("expected a 128-bit AES key");
            byte[] iv=args.checkByteArray(2);
            if(iv.length!=16) throw new IllegalArgumentException("expected a 128-bit AES IV");
            var cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(mode,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv)); return new Object[]{cipher.doFinal(data)};
        }
        @Callback(direct=true,limit=8,doc="function(data:string,key:string,iv:string):string -- Encrypt data using AES-128 CBC.")
        public Object[] encrypt(Context context,Arguments args) throws Exception { return crypt(context,args,Cipher.ENCRYPT_MODE); }
        @Callback(direct=true,limit=8,doc="function(data:string,key:string,iv:string):string -- Decrypt AES-128 CBC data.")
        public Object[] decrypt(Context context,Arguments args) throws Exception { return crypt(context,args,Cipher.DECRYPT_MODE); }
        @Callback(direct=true,limit=4,doc="function(len:number):string -- Generate 1 to 1024 secure random bytes.")
        public Object[] random(Context context,Arguments args) throws Exception {
            int len=args.checkInteger(0); if(len<=0||len>1024) throw new IllegalArgumentException("length must be in range [1..1024]");
            checkCost(Settings.get().dataCardComplex+Settings.get().dataCardComplexByte*len);
            byte[] data=new byte[len]; RANDOM.get().nextBytes(data); return new Object[]{data};
        }
    }
    public static class Tier3 extends Tier2 {
        @Override public Map<String,String> getDeviceInfo() { return info("SC03D Signer"); }
        @Callback(direct=true,limit=1,doc="function([bitLen:number]):userdata,userdata -- Generate a public/private EC key pair (256 or 384 bits).")
        public Object[] generateKeyPair(Context context,Arguments args) throws Exception {
            checkCost(Settings.get().dataCardAsymmetric); int bits=args.optInteger(0,384);
            if(bits!=256&&bits!=384) throw new IllegalArgumentException("invalid key length, must be 256 or 384");
            var generator=KeyPairGenerator.getInstance("EC"); generator.initialize(bits,RANDOM.get()); var keys=generator.generateKeyPair();
            return new Object[]{new ECUserdata(keys.getPublic()),new ECUserdata(keys.getPrivate())};
        }
        @Callback(direct=true,limit=8,doc="function(data:string,type:string):userdata -- Deserialize an EC key.")
        public Object[] deserializeKey(Context context,Arguments args) throws Exception {
            byte[] data=simple(context,args);return new Object[]{new ECUserdata(ECUserdata.deserialize(args.checkString(1),data))};
        }
        private ECUserdata key(Arguments args,int index,Boolean publicKey) {
            Object value=args.checkAny(index);
            if(!(value instanceof ECUserdata key)) throw new IllegalArgumentException("bad argument #"+(index+1)+" (userdata expected, got "+(value==null?"no value":value.getClass().getName())+")");
            if(publicKey!=null&&publicKey!=key.isPublic()) throw new IllegalArgumentException((publicKey?"public":"private")+" key expected at "+(index+1));
            return key;
        }
        @Callback(direct=true,limit=1,doc="function(priv:userdata,pub:userdata):string -- Generate an ECDH shared secret.")
        public Object[] ecdh(Context context,Arguments args) throws Exception {
            checkCost(Settings.get().dataCardAsymmetric); var agreement=KeyAgreement.getInstance("ECDH");
            agreement.init(key(args,0,false).value); agreement.doPhase(key(args,1,true).value,true); return new Object[]{agreement.generateSecret()};
        }
        @Callback(direct=true,limit=1,doc="function(data:string,key:userdata[,sig:string]):string or boolean -- Sign or verify data using ECDSA.")
        public Object[] ecdsa(Context context,Arguments args) throws Exception {
            byte[] data=cost(context,args,Settings.get().dataCardAsymmetric,Settings.get().dataCardComplexByte);
            var key=key(args,1,null); byte[] signature=args.optByteArray(2,null); var signer=Signature.getInstance("SHA256withECDSA");
            if(signature!=null) {
                if(!(key.value instanceof PublicKey publicKey)) throw new IllegalArgumentException("public key expected");
                signer.initVerify(publicKey); signer.update(data); return new Object[]{signer.verify(signature)};
            }
            if(!(key.value instanceof PrivateKey privateKey)) throw new IllegalArgumentException("private key expected");
            signer.initSign(privateKey); signer.update(data); return new Object[]{signer.sign()};
        }
    }
    public static final class ECUserdata extends AbstractValue {
        private Key value;
        public ECUserdata() {}
        private ECUserdata(Key value) { this.value=value; }
        private boolean isPublic() { return value instanceof ECPublicKey; }
        private String keyType() { return isPublic()?"ec-public":"ec-private"; }
        private static Key deserialize(String type,byte[] data) throws GeneralSecurityException {
            var factory=KeyFactory.getInstance("EC");
            return switch(type) {
                case "ec-private" -> factory.generatePrivate(new PKCS8EncodedKeySpec(data));
                case "ec-public" -> factory.generatePublic(new X509EncodedKeySpec(data));
                default -> throw new IllegalArgumentException("invalid key type, must be ec-public or ec-private");
            };
        }
        @Callback(direct=true,doc="function():boolean -- Whether this is a public key.")
        public Object[] isPublic(Context context,Arguments args) { return new Object[]{isPublic()}; }
        @Callback(direct=true,doc="function():string -- Return the serialized key type.")
        public Object[] keyType(Context context,Arguments args) { return new Object[]{keyType()}; }
        @Callback(direct=true,limit=4,doc="function():string -- Serialize this key as binary DER data.")
        public Object[] serialize(Context context,Arguments args) { return new Object[]{value.getEncoded()}; }
        @Override public void load(CompoundTag tag) {
            try { value=deserialize(tag.getString("Type"),tag.getByteArray("Data")); }
            catch(GeneralSecurityException e) { throw new IllegalArgumentException("invalid EC key",e); }
        }
        @Override public void save(CompoundTag tag) { tag.putString("Type",keyType()); tag.putByteArray("Data",value.getEncoded()); }
    }
}
