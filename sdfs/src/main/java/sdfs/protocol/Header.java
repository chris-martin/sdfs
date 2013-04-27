package sdfs.protocol;

import com.google.common.hash.HashCode;
import org.joda.time.Instant;
import sdfs.CN;
import sdfs.sdfs.Right;

public abstract class Header {

    public CorrelationId correlationId;

    protected void respondsTo(Header request) {
        correlationId = request.correlationId;
    }

    public static class Bye extends Header {
        public void accept(Visitor visitor) throws Exception { visitor.visit(this); }
    }

    static abstract class File extends Header {
        public String filename;

        protected void respondsTo(File request) {
            super.respondsTo(request);
            filename = request.filename;
        }
    }

    public static class Prohibited extends File {
        public void accept(Visitor visitor) throws Exception { visitor.visit(this); }
    }

    public static Prohibited prohibited(File request) {
        Prohibited prohibited = new Prohibited();
        prohibited.respondsTo(request);
        return prohibited;
    }

    public static class Unavailable extends File {
        public void accept(Visitor visitor) throws Exception { visitor.visit(this); }
    }

    public static Unavailable unavailable(File request) {
        Unavailable unavailable = new Unavailable();
        unavailable.respondsTo(request);
        return unavailable;
    }

    public static class Nonexistent extends File {
        public void accept(Visitor visitor) throws Exception { visitor.visit(this); }
    }

    public static Nonexistent nonexistent(File request) {
        Nonexistent unavailable = new Nonexistent();
        unavailable.respondsTo(request);
        return unavailable;
    }

    public static class Ok extends File {
        public void accept(Visitor visitor) throws Exception { visitor.visit(this); }
    }

    public static Ok ok(File request) {
        Ok unavailable = new Ok();
        unavailable.respondsTo(request);
        return unavailable;
    }

    public static class Get extends File {
        public void accept(Visitor visitor) throws Exception { visitor.visit(this); }
    }

    public static class Put extends File {
        public HashCode hash;
        public long size;

        public void accept(Visitor visitor) throws Exception { visitor.visit(this); }
    }

    public static class Delegate extends File {
        public CN to;
        public Iterable<Right> rights;
        public Instant expiration;

        public void accept(Visitor visitor) throws Exception { visitor.visit(this); }
    }

    public abstract void accept(Visitor visitor) throws Exception;

    public interface Visitor {
        void visit(Bye bye) throws Exception;
        void visit(Prohibited prohibited) throws Exception;
        void visit(Unavailable unavailable) throws Exception;
        void visit(Nonexistent nonexistent) throws Exception;
        void visit(Ok ok) throws Exception;
        void visit(Get get) throws Exception;
        void visit(Put put) throws Exception;
        void visit(Delegate delegate) throws Exception;
    }
}
