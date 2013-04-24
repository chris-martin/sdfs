package sdfs.protocol;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.io.CountingOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static com.google.common.base.Preconditions.checkState;

public class InboundFile {

    private static final Logger log = LoggerFactory.getLogger(InboundFile.class);

    private final CountingOutputStream dest;
    private final Hasher hasher;
    public final long size;

    private HashCode hash;

    public InboundFile(OutputStream dest, HashFunction hashFunction, long size) {
        hasher = hashFunction.newHasher((int) size);
        this.dest = new CountingOutputStream(new HashingOutputStream(dest, hasher));
        this.size = size;
    }

    public OutputStream dest() {
        return dest;
    }

    public long count() {
        return dest.getCount();
    }

    public HashCode hash() {
        checkState(size == dest.getCount());
        if (hash == null) {
            hash = hasher.hash();
        }
        return hash;
    }

    private final class HashingOutputStream extends FilterOutputStream {

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
