package sdfs.crypto;

import java.security.KeyPair;

public class UnlockedAsymmetricBlockCipher implements UnlockedBlockCipher {

    private final AsymmetricBlockCipher cipher;
    private final KeyPair keyPair;

    public UnlockedAsymmetricBlockCipher(AsymmetricBlockCipher cipher, KeyPair keyPair) {
        this.cipher = cipher;
        this.keyPair = keyPair;
    }

    @Override
    public byte[] encrypt(byte[] plaintext) {
        return cipher.encrypt(keyPair.getPublic(), plaintext);
    }

    @Override
    public byte[] decrypt(byte[] ciphertext) {
        return cipher.decrypt(keyPair.getPrivate(), ciphertext);
    }
}
