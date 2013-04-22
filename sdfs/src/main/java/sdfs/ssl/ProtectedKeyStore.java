package sdfs.ssl;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class ProtectedKeyStore {

    public final KeyStore keyStore;
    public final char[] password;

    public ProtectedKeyStore(char[] password) {
        this(emptyKeyStore(), password);
    }

    public ProtectedKeyStore(KeyStore keyStore, char[] password) {
        this.keyStore = keyStore;
        this.password = password;
    }

    private static KeyStore emptyKeyStore() {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            return keyStore;
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }

}
