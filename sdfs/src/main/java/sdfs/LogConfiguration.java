package sdfs;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

public class LogConfiguration {

    private LogConfiguration() {}

    public static void disableLogging() {
        getLoggerContext().stop();
    }

    private static LoggerContext getLoggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

}
