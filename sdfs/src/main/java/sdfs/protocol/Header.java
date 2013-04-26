package sdfs.protocol;

import com.google.common.hash.HashCode;
import org.joda.time.Instant;
import sdfs.CN;
import sdfs.sdfs.Right;

public class Header {

    public CorrelationId correlationId;

    protected void respondsTo(Header request) {
        correlationId = request.correlationId;
    }

    public static class Bye extends Header { }

    static class File extends Header {
        public String filename;

        protected void respondsTo(File request) {
            super.respondsTo(request);
            filename = request.filename;
        }
    }

    public static class Prohibited extends File { }

    public static Prohibited prohibited(File request) {
        Prohibited prohibited = new Prohibited();
        prohibited.respondsTo(request);
        return prohibited;
    }

    public static class Unavailable extends File { }

    public static Unavailable unavailable(File request) {
        Unavailable unavailable = new Unavailable();
        unavailable.respondsTo(request);
        return unavailable;
    }

    public static class Get extends File { }

    public static class Put extends File {
        public HashCode hash;
        public long size;
    }

    public static class Delegate extends File {
        public CN to;
        public Iterable<Right> rights;
        public Instant expiration;
    }
}
