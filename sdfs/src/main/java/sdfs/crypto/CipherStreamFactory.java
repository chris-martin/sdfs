package sdfs.crypto;

import com.google.common.io.BaseEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.*;

public class CipherStreamFactory {

    private static final Logger log = LoggerFactory.getLogger(CipherStreamFactory.class);

    private final Crypto crypto;

    public CipherStreamFactory(Crypto crypto) {
        this.crypto = crypto;
    }

    public CipherOutputStream encrypt(OutputStream out, byte[] key) throws IOException {
        Cipher cipher = crypto.newCipherForEncryption(key);

        byte[] iv = cipher.getIV();
        log.debug("IV = {}", BaseEncoding.base16().lowerCase().encode(iv));

        new DataOutputStream(out).writeInt(iv.length);
        out.write(iv);

        return new CipherOutputStream(out, cipher);
    }

    public CipherInputStream decrypt(InputStream in, byte[] key) throws IOException {
        DataInput dataIn = new DataInputStream(in);
        int ivLen = dataIn.readInt();
        byte[] iv = new byte[ivLen];
        dataIn.readFully(iv);

        Cipher cipher = crypto.newCipherForDecryption(key, iv);
        return new CipherInputStream(in, cipher);
    }
}
