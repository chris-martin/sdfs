package sdfs;

import org.joda.time.Instant;
import sdfs.time.Chronos;

public class MockChronos implements Chronos {

    public Instant now = new Instant(0);

    public Instant now() {
        return now;
    }

}
