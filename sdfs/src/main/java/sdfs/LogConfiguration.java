package sdfs;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.joran.spi.JoranException;
import org.slf4j.LoggerFactory;

public class LogConfiguration {

    private LogConfiguration() {}

    public static void setLoggingEnabled(boolean enabled) {
        if (enabled) {
            enableLogging();
        } else {
            disableLogging();
        }
    }

    public static void enableLogging() {
        getLoggerContext().reset();
        try {
            new ContextInitializer(getLoggerContext()).autoConfig();
        } catch (JoranException ignored) {
        }
    }

    public static void disableLogging() {
        getLoggerContext().stop();
    }

    private static LoggerContext getLoggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

}
