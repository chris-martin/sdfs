package sdfs.protocol;

import com.google.common.io.CountingOutputStream;

public class FileTransfer {

    public final CountingOutputStream dest;
    public final long size;

    public FileTransfer(CountingOutputStream dest, long size) {
        this.dest = dest;
        this.size = size;
    }
}
