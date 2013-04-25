package sdfs.protocol;

import com.google.common.base.Stopwatch;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.io.CountingOutputStream;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static com.google.common.base.Preconditions.checkState;

public class InboundFile {

    private static final Logger log = LoggerFactory.getLogger(InboundFile.class);

    private final OutputStream dest;
    public final long size;
    private long count;
    private final Hasher hasher;
    private final HashCode expectedHash;
    private final Stopwatch stopwatch;

    private HashCode hash;

    public InboundFile(OutputStream dest, long size, HashFunction hashFunction, HashCode expectedHash) {
        hasher = hashFunction.newHasher((int) size);
        this.expectedHash = expectedHash;
        this.dest = new CountingOutputStream(new HashingOutputStream(dest, hasher));
        this.size = size;
        stopwatch = new Stopwatch().start();
    }

    /** Reads from the given input buffer. Returns true iff done receiving the file and hash matches correctly. */
    public boolean read(ByteBuf in) throws IOException {
        in.readBytes(dest, in.readableBytes());
        if (count == size) {
            close();
            return true;
        }
        return false;
    }

    void close() throws IOException {
        log.info("Received file ({} bytes) in {}", size, stopwatch.stop());
        dest.flush();
        dest.close();
        checkHashMatches();
    }

    private void checkHashMatches() throws HashMismatchException {
        if (!expectedHash.equals(hash())) {
            throw new HashMismatchException(expectedHash, hash());
        }
    }

    private HashCode hash() {
        checkState(count == size);
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
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            hasher.putBytes(b, off, len);
            count += len;
        }
    }
}
