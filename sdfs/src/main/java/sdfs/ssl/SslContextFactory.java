package sdfs.ssl;

import com.typesafe.config.Config;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

public class SslContextFactory {

    private final Config config;

    public SslContextFactory(Config config) {
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
