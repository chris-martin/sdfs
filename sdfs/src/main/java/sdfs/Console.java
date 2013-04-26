package sdfs;

import com.google.common.base.*;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.tools.jline.console.ConsoleReader;
import scala.tools.jline.console.history.FileHistory;
import sdfs.client.Client;
import sdfs.sdfs.AccessType;
import sdfs.sdfs.DelegationType;
import sdfs.sdfs.Right;
import sdfs.server.Server;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Console {

    private static final Logger log = LoggerFactory.getLogger(Console.class);

    Config config = ConfigFactory.empty();
    ConsoleReader console;
    Thread shutdownHook;
    Splitter commandSplitter = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings();
    Server server;
    Client client;
    boolean halt;

    void run() {
        LogConfiguration.disableLogging();
        try {
            console = new ConsoleReader();
            console.setHistory(new FileHistory(new File("sdfs.history")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        shutdownHook = new Thread(new Runnable() {
            public void run() {
                shutdownHook = null;
                stop();
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        while (!halt) {

            String line;
            try {
                line = console.readLine("sdfs> ");
                if (line == null) {
                    line = "q";
                }
            } catch (IOException e) {
                halt = true;
                continue;
            }

            execute(ImmutableList.copyOf(commandSplitter.split(line)));
        }

        if (client != null) {
            client.disconnect();
            client = null;
        }
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    void stop() {
        if (console != null) {
            try {
                console.getTerminal().restore();
                console = null;
            } catch (Throwable ignored) {
            }
        }
        if (shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null;
        }
    }

    String help() {

        StringBuilder str = new StringBuilder();

        try {

            str.append(Resources.toString(Resources.getResource("help.txt"), Charsets.UTF_8)
            ).append("\n");

        } catch (IOException e) {
            str.append("Error: ").append(Throwables.getStackTraceAsString(e)).append("\n");
        }

        return str.toString();
    }

    String config() {

        StringBuilder str = new StringBuilder();

        str.append("Config:\n\n").append(
            (
                "sdfs " + config.getValue("sdfs").render(
                    ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)
                ) + "\n"
            ).replaceAll("(.+)\n", "    $1\n")
        );

        return str.toString();
    }

    String status() {

        StringBuilder str = new StringBuilder();

        StringBuilder status = new StringBuilder();
        if (server != null) {
            status.append(server.toString()).append("\n");
        }
        if (client != null) {
            status.append(client.toString()).append("\n");
        }
        if (status.length() == 0) {
            status.append("Idle\n");
        }

        str.append("\nStatus:\n\n").append(
            status.toString().replaceAll("(.+)\n", "    $1\n")
        );

        return str.toString();
    }

    synchronized void execute(List<String> commandArgs) {

        if (commandArgs.isEmpty()) {
            return;
        }

        String head = commandArgs.get(0);
        List<String> tail = commandArgs.subList(1, commandArgs.size());

        if (ImmutableList.of("?").contains(head)) {

            System.out.println(help());
            System.out.println(config());
            System.out.println(status());

        } else if (ImmutableList.of("log").contains(head)) {

            LogConfiguration.setLoggingEnabled(!tail.contains("stop"));

        } else if (ImmutableList.of("help").contains(head)) {

            System.out.println(help());

        } else if (ImmutableList.of("quit", "q").contains(head)) {

            halt = true;

        } else if (ImmutableList.of("set").contains(head)) {

            if (tail.size() == 2) {

                String key = tail.get(0);
                String value = tail.get(1);

                config = ConfigFactory.parseMap(ImmutableMap.of(key, value)).atPath("sdfs").withFallback(config);

            }

        } else if ("config".equals(head)) {

            System.out.println(config());

        } else if ("status".equals(head)) {

            System.out.println(status());

        } else if (ImmutableList.of("server", "s").contains(head)) {

            if (tail.size() == 1 && tail.get(0).equals("stop")) {
                if (server == null) {
                    System.out.println("Server not started.");
                } else {
                    System.out.println("Stopping server...");
                    server.stop();
                    server = null;
                }
            } else if (server != null) {
                System.out.println("Server already started.");
            } else {
                try {
                    server = Server.fromConfig(config);
                    System.out.println("Starting server on port " + server.port + "...");
                    server.start();
                    System.out.println("Server started on port " + server.port + ".");

                } catch (Exception e) {
                    System.out.println("Error: " + Throwables.getStackTraceAsString(e));
                    server = null;
                }
            }

        } else if (ImmutableList.of("client", "c").contains(head)) {

            if (tail.size() == 1 && tail.get(0).equals("stop")) {
                if (client == null) {
                    System.out.println("Client not started.");
                } else {
                    System.out.println("Stopping client...");
                    client.disconnect();
                    client = null;
                    System.out.println("Client stopped.");
                }
            } else if (client != null) {
                System.out.println("Already connected.");
            } else {
                try {
                    client = Client.fromConfig(config);
                    System.out.println("Client connecting to " + client.serverAddr + "...");
                    client.connect(new Runnable() {
                        public void run() {
                            System.out.println("Client disconnected from " + client.serverAddr + ".");
                            client = null;
                        }
                    });
                    System.out.println("Client connected to " + client.serverAddr + ".");
                } catch (Exception e) {
                    System.out.println("Error: " + Throwables.getStackTraceAsString(e));
                    client = null;
                }
            }
        } else if (client != null) {
            if (head.equals("get") && tail.size() == 1) {
                String filename = tail.get(0);
                System.out.println("Getting file " + filename);
                client.get(filename);
            } else if (head.equals("put") && tail.size() == 1) {
                String filename = tail.get(0);
                System.out.println("Putting file " + filename);
                client.put(filename);
            } else if (head.startsWith("delegate") && tail.size() >= 3) {
                final DelegationType delegationType = head.endsWith("*") ? DelegationType.Star : DelegationType.None;

                String filename = tail.get(0);
                CN delegateClient = new CN(tail.get(1));
                Duration duration = new Duration(Long.parseLong(tail.get(2)));// Duration.parse(tail.get(2));

                List<AccessType> accessTypes;
                if (tail.size() < 4) {
                    accessTypes = ImmutableList.copyOf(AccessType.values());
                } else {
                    accessTypes = Lists.newArrayList();
                    if (tail.contains("get")) {
                        accessTypes.add(AccessType.Get);
                    }
                    if (tail.contains("put")) {
                        accessTypes.add(AccessType.Put);
                    }
                }
                Iterable<Right> rights = FluentIterable
                        .from(accessTypes)
                        .transform(new Function<AccessType, Right>() {
                            public Right apply(AccessType type) {
                                return new Right(type, delegationType);
                            }
                        });

                client.delegate(delegateClient, filename, rights, duration);
            } else {
                System.out.println(help());
            }
        } else {
            System.out.println(help());
        }

    }

    public static void main(String[] args) {
        Console console = new Console();
        console.config = config(args);
        console.run();
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
