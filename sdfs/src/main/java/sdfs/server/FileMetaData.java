package sdfs.server;

import java.io.*;
import java.util.Arrays;

public class FileMetaData {

    public final long size;
    public final byte[] encryptedHash;

    public FileMetaData(long size, byte[] encryptedHash) {
        this.size = size;
        this.encryptedHash = Arrays.copyOf(encryptedHash, encryptedHash.length);
    }

    public void writeTo(OutputStream out) throws IOException {
        DataOutput data = new DataOutputStream(out);
        data.writeLong(size);
        data.writeInt(encryptedHash.length);
        data.write(encryptedHash);
        out.flush();
    }

    public static FileMetaData readFrom(InputStream in) throws IOException {
        DataInput data = new DataInputStream(in);
        long size = data.readLong();
        int hashSize = data.readInt();
        byte[] encryptedHash = new byte[hashSize];
        data.readFully(encryptedHash);
        return new FileMetaData(size, encryptedHash);
    }
}
