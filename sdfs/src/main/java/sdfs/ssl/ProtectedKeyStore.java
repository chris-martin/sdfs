package sdfs.ssl;

import java.io.IOException;
import java.io.InputStream;
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

    public static ProtectedKeyStore createEmpty(InputStream prototypeKeystore, char[] password) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(prototypeKeystore, password);
            return new ProtectedKeyStore(keyStore, password);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
