package sdfs.sdfs;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import org.joda.time.Instant;
import sdfs.CN;

import java.io.IOException;

public interface SDFS {

    Get get(CN cn, String resourceName)
            throws AccessControlException, ResourceNonexistentException, ResourceUnavailableException;

    Put put(CN cn, String resourceName)
            throws AccessControlException, ResourceUnavailableException;

    void delegate(CN from, CN to, String resourceName, Right right, Instant expiration)
            throws AccessControlException, ResourceNonexistentException;

    interface Put {

        ByteSink contentByteSink() throws IOException;

        ByteSink metaByteSink() throws IOException;

        void release() throws IOException;

        void abort() throws IOException;

    }

    interface Get {

        ByteSource contentByteSource();

        ByteSource metaByteSource();

        void release();

    }

}
