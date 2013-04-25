package sdfs.crypto;

import com.typesafe.config.Config;
import sdfs.crypto.Rsa;
import sdfs.crypto.UnlockedAsymmetricBlockCipher;
import sdfs.crypto.UnlockedBlockCipher;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.*;

import static java.util.Objects.requireNonNull;

public class Crypto {

    private final Config config;

    public Crypto(Config config) {
        this.config = config;
    }

    public Cipher newCipherForEncryption(byte[] key) {
        try {
            Cipher cipher = newCipher();
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(key));
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public Cipher newCipherForDecryption(byte[] key, byte[] iv) {
        try {
            Cipher cipher = newCipher();
            cipher.init(Cipher.DECRYPT_MODE, aesKey(key), new IvParameterSpec(iv));
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    private SecretKeySpec aesKey(byte[] key) {
        return new SecretKeySpec(key, 0, 256/Byte.SIZE, "AES");
    }

    private Cipher newCipher() throws NoSuchAlgorithmException, NoSuchPaddingException {
        return Cipher.getInstance("AES/CBC/PKCS5Padding");
    }

    public SSLContext newSslContext() {
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
