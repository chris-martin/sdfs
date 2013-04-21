package sdfs.ssl;

import java.security.KeyStore;

public class ProtectedKeyStore {

    public final KeyStore keyStore;
    public final char[] password;

    public ProtectedKeyStore(KeyStore keyStore, char[] password) {
        this.keyStore = keyStore;
        this.password = password;
    }
}
