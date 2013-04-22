package sdfs;

import org.joda.time.Instant;

public class MockChronos implements Chronos {

    public Instant now = new Instant(0);

    public Instant now() {
        return now;
    }

}
