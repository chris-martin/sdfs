package sdfs.crypto;

public interface UnlockedBlockCipher {

    byte[] encrypt(byte[] plaintext);

    byte[] decrypt(byte[] ciphertext);
}
