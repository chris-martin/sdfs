package sdfs.protocol;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.io.CountingOutputStream;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class InboundFile {

    private final CountingOutputStream dest;
    public final HashCode expectedHash;
    private final Hasher hasher;
    public final long size;

    public InboundFile(OutputStream dest, HashCode expectedHash, HashFunction hashFunction, long size) {
        hasher = hashFunction.newHasher((int) size);
        this.dest = new CountingOutputStream(new HashingOutputStream(dest, hasher));
        this.expectedHash = expectedHash;
        this.size = size;
    }

    public OutputStream dest() {
        return dest;
    }

    public long count() {
        return dest.getCount();
    }

    public HashCode hash() {
        return hasher.hash();
    }

    public boolean hashMatches() {
        return expectedHash.equals(hash());
    }

    private static final class HashingOutputStream extends FilterOutputStream {

        private final Hasher hasher;

        private HashingOutputStream(OutputStream out, Hasher hasher) {
            super(out);
            this.hasher = hasher;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            hasher.putByte((byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            hasher.putBytes(b, off, len);
        }
    }
}
