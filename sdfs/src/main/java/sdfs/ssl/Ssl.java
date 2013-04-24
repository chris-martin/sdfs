package sdfs.ssl;

import com.typesafe.config.Config;
import sdfs.crypto.Rsa;
import sdfs.crypto.UnlockedAsymmetricBlockCipher;
import sdfs.crypto.UnlockedBlockCipher;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;

import static java.util.Objects.requireNonNull;

public class Ssl {

    private final Config config;

    public Ssl(Config config) {
        this.config = config;
    }

    public SSLContext newContext() {
        try {
            SSLContext context = SSLContext.getInstance(config.getString("sdfs.protocol"));
            context.init(keyManagers(), trustManagers(), null);
            return context;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public UnlockedBlockCipher unlockedBlockCipher() {
        return new UnlockedAsymmetricBlockCipher(new Rsa(), findRsaKeyPair("RSA"));
    }

    KeyPair findRsaKeyPair(String algorithm) {
        KeyManager[] keyManagers;
        try {
            keyManagers = keyManagers();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        for (KeyManager keyManager : keyManagers) {
            if (keyManager instanceof X509ExtendedKeyManager) {
                X509ExtendedKeyManager x509KeyManager = (X509ExtendedKeyManager) keyManager;
                String alias = x509KeyManager.chooseEngineServerAlias(algorithm, null, null);
                if (alias != null) {
                    PublicKey publicKey = x509KeyManager.getCertificateChain(alias)[0].getPublicKey();
                    PrivateKey privateKey = x509KeyManager.getPrivateKey(alias);
                    return new KeyPair(publicKey, privateKey);
                }
            }
        }
        throw new RuntimeException("No RSA key found in key store");
    }

    KeyManager[] keyManagers() throws Exception {
        KeyStore store = KeyStore.getInstance("JKS");
        try (FileInputStream in = new FileInputStream(new File(config.getString("sdfs.keystore.personal.file")))) {
            store.load(in, config.getString("sdfs.keystore.personal.store-password").toCharArray());
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(store, config.getString("sdfs.keystore.personal.key-password").toCharArray());
        return keyManagerFactory.getKeyManagers();
    }

    TrustManager[] trustManagers() throws Exception {
        KeyStore store = KeyStore.getInstance("JKS");
        try (InputStream in = new FileInputStream(new File(config.getString("sdfs.keystore.ca.file")))) {
            store.load(in, config.getString("sdfs.keystore.ca.store-password").toCharArray());
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(store);
        return trustManagerFactory.getTrustManagers();
    }

}
