import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

public final class SignApk {
    public static void main(String[] args) throws Exception {
        char[] password=args[3].toCharArray();
        KeyStore store=KeyStore.getInstance("JKS");
        try(InputStream stream=new FileInputStream(args[2])) { store.load(stream,password); }
        PrivateKey key=(PrivateKey)store.getKey(args[4],password);
        X509Certificate cert=(X509Certificate)store.getCertificate(args[4]);
        ApkSigner.SignerConfig config=new ApkSigner.SignerConfig.Builder("SmitchLocal",key,Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(config)).setInputApk(new File(args[0])).setOutputApk(new File(args[1]))
            .setMinSdkVersion(23).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).setV4SigningEnabled(false).build().sign();
        ApkVerifier.Result result=new ApkVerifier.Builder(new File(args[1])).build().verify();
        if(!result.isVerified() || !result.isVerifiedUsingV1Scheme() || !result.isVerifiedUsingV2Scheme() || !result.isVerifiedUsingV3Scheme())
            throw new IllegalStateException("Signature verification failed: "+result.getAllErrors());
        System.out.println("APK signatures verified: v1, v2, v3.");
    }
}
