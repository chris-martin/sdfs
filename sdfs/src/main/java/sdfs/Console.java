package sdfs;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import scala.tools.jline.console.ConsoleReader;

import java.io.File;
import java.io.IOException;
import java.util.List;

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

        new Thread() {
            public void run() {
                boolean halt = false;
                while (!halt) {
                    try {
                        String commandLine = startedState.console.readLine("sdfs> ");
                        List<String> commandArgs = ImmutableList.copyOf(commandSplitter.split(commandLine));
                        if (!commandArgs.isEmpty()) {
                            String head = commandArgs.get(0);
                            List<String> tail = commandArgs.subList(1, commandArgs.size());

                            if (ImmutableList.of("help", "?").contains(head)) {

                                String help = Resources.toString(Resources.getResource("help.txt"), Charsets.UTF_8);

                                String ref = Resources.toString(Resources.getResource("reference.conf"), Charsets.UTF_8
                                ).replaceAll("(.+)\n", "    $1\n");

                                System.out.println(help + "Config format:\n\n" + ref);

                            } else if (ImmutableList.of("quit", "q").contains(head)) {

                                halt = true;

                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }.start();

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

    static final Splitter commandSplitter = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings();

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
