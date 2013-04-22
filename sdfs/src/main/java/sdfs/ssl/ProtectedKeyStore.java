package sdfs.ssl;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class ProtectedKeyStore {

    public final KeyStore keyStore;
    public final char[] password;

    public ProtectedKeyStore(KeyStore keyStore, char[] password) {
        this.keyStore = keyStore;
        this.password = password;
    }

    public static ProtectedKeyStore createEmpty() {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            char[] password = "changeit".toCharArray();
            keyStore.load(null, password);
            return new ProtectedKeyStore(keyStore, password);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
