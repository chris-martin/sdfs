package sdfs;

import org.joda.time.Instant;

public class ChronosImpl implements Chronos {

    public Instant now() {
        return Instant.now();
    }

}
