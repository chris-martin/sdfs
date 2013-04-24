package sdfs.protocol;

import com.google.common.hash.HashCode;
import org.joda.time.Instant;
import sdfs.CN;
import sdfs.sdfs.Right;

public class Header {

    public CorrelationId correlationId;

    public static class Bye extends Header { }

    public static class Prohibited extends Header { }

    static class File extends Header {
        public String filename;
    }

    public static class Get extends File { }

    public static class Put extends File {
        public HashCode hash;
        public long size;
    }

    public static class Delegate extends File {
        public CN to;
        public Right right;
        public Instant expiration;
    }
}
