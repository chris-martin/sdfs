package sdfs;

import com.google.common.base.Joiner;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import scala.tools.jline.console.ConsoleReader;

import java.io.File;
import java.io.IOException;

public class Console {

    Config config = ConfigFactory.empty();

    static class StartedState {
        ConsoleReader console;
        Thread shutdownHook;
    }

    StartedState startedState;

    void start() {
        System.out.println(config);

        if (startedState == null) {
            startedState = new StartedState();
            try {
                startedState.console = new ConsoleReader();
            } catch (IOException e) {
                startedState = null;
                throw new RuntimeException(e);
            }
            startedState.shutdownHook = new Thread(new Runnable() {
                public void run() {
                    stop();
                }
            });
            Runtime.getRuntime().addShutdownHook(startedState.shutdownHook);
        }

        try {
            String a = startedState.console.readLine();
            System.out.println(a);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    void stop() {
        if (startedState != null) {
            try {
                startedState.console.getTerminal().restore();
            } catch (Throwable ignored) {
            }
            Runtime.getRuntime().removeShutdownHook(startedState.shutdownHook);
            startedState = null;
        }
    }

    public static void main(String[] args) {
        Console console = new Console();
        console.config = config(args);
        console.start();
    }

    static Config config(String[] args) {
        return argsConfig(args)
            .withFallback(fileConfig())
            .withFallback(ConfigFactory.load());
    }

    static Config argsConfig(String[] args) {
        Config config = ConfigFactory.empty();
        for (String arg : args) {
            config = config.withFallback(ConfigFactory.parseString(arg).atPath("sdfs"));
        }
        return config;
    }

    static Config fileConfig() {
        return ConfigFactory.parseFile(new File("sdfs.conf"));
    }

}
