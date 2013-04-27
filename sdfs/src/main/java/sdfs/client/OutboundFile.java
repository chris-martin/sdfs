package sdfs.client;

import com.google.common.io.ByteSource;
import sdfs.protocol.Header;

final class OutboundFile {
    final Header.Put put;
    final ByteSource file;

    OutboundFile(Header.Put put, ByteSource file) {
        this.put = put;
        this.file = file;
    }
}
