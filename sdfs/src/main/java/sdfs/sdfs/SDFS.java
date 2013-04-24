package sdfs.sdfs;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import org.joda.time.Instant;
import sdfs.CN;

import java.io.IOException;

public interface SDFS {

    Get get(CN cn, String resourceName);

    Put put(CN cn, String resourceName);

    void delegate(CN from, CN to, String resourceName, Right right, Instant expiration);

    interface Put {

        ByteSink contentByteSink() throws IOException;

        ByteSink metaByteSink() throws IOException;

        void release() throws IOException;

    }

    interface Get {

        ByteSource contentByteSource();

        ByteSource metaByteSource();

        void release();

    }

}
