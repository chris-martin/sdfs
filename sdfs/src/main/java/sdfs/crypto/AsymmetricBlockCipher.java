package sdfs.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;

public interface AsymmetricBlockCipher {

    public byte[] encrypt(PublicKey key, byte[] plaintext);

    public byte[] decrypt(PrivateKey key, byte[] ciphertext);
}
