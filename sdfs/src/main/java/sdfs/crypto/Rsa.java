package sdfs.crypto;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.*;

public class Rsa implements AsymmetricBlockCipher {

    @Override
    public byte[] encrypt(PublicKey key, byte[] plaintext) {
        return rsa(Cipher.ENCRYPT_MODE, key, plaintext);
    }

    @Override
    public byte[] decrypt(PrivateKey key, byte[] ciphertext) {
        return rsa(Cipher.DECRYPT_MODE, key, ciphertext);
    }

    private byte[] rsa(int mode, Key key, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(mode, key);
            return cipher.doFinal(input);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
