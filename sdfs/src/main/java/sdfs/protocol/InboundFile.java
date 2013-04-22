package sdfs.protocol;

import com.google.common.io.CountingOutputStream;

public class InboundFile {

    public final CountingOutputStream dest;
    public final long size;

    public InboundFile(CountingOutputStream dest, long size) {
        this.dest = dest;
        this.size = size;
    }
}
