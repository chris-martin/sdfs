package sdfs.ssl;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.*;

public class SslContextFactory {

    private final String protocol = "TLS";

    private final ProtectedKeyStore keyStore;
    private final KeyStore trustManagerKeyStore;

    public SslContextFactory(ProtectedKeyStore keyStore, KeyStore trustManagerKeyStore) {
        this.keyStore = keyStore;
        this.trustManagerKeyStore = trustManagerKeyStore;
    }

    public SSLContext newContext() {
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore.keyStore, keyStore.password);

            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustManagerKeyStore);

            SSLContext context = SSLContext.getInstance(protocol);
            context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
            return context;
        } catch (NoSuchAlgorithmException | KeyManagementException | UnrecoverableKeyException | KeyStoreException e) {
            throw new RuntimeException(e.getMessage(), e); // TODO
        }
    }
}
