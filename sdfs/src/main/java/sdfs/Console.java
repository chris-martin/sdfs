package sdfs;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import scala.tools.jline.console.ConsoleReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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

                Splitter commandSplitter = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings();

                boolean halt = false;
                while (!halt) {

                    try {
                        String line = startedState.console.readLine("sdfs> ");
                        List<String> args = ImmutableList.copyOf(commandSplitter.split(line));
                        if (!args.isEmpty()) {
                            ExecutionResult result = execute(args);
                            if (result.halt) {
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

    ExecutionResult execute(List<String> commandArgs) {

        String head = commandArgs.get(0);
        List<String> tail = commandArgs.subList(1, commandArgs.size());

        if (ImmutableList.of("help", "?").contains(head)) {

            try {

                String help = CharStreams.toString(new InputStreamReader(
                    Resources.getResource("help.txt").openStream())
                );

                String ref = CharStreams.toString(new InputStreamReader(
                    Resources.getResource("reference.conf").openStream())
                ).replaceAll("(.+)\n", "    $1\n");

                System.out.println(help + "Config format:\n\n" + ref);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } else if (ImmutableList.of("quit", "q").contains(head)) {

            return ExecutionResult.halt();

        }

        return ExecutionResult.ok();
    }

    static class ExecutionResult {

        boolean halt;

        static ExecutionResult ok() {
            return new ExecutionResult();
        }

        static ExecutionResult halt() {
            ExecutionResult x = new ExecutionResult();
            x.halt = true;
            return x;
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
