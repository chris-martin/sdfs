package sdfs;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

public class TestLogConfiguration {

    private static final boolean LOGGING_ENABLED = false;

    public static void configureLogging() {
        if (LOGGING_ENABLED) {
            // ensure logging initialized
            LoggerFactory.getLogger("ROOT");
        } else {
            LogConfiguration.disableLogging();
        }
    }

    private TestLogConfiguration() {}
}
