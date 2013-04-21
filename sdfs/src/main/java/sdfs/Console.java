package sdfs;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import scala.tools.jline.console.ConsoleReader;
import sdfs.client.Client;
import sdfs.server.Server;
import sdfs.ssl.ProtectedKeyStore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

public class Console {

    Config config = ConfigFactory.empty();
    ConsoleReader console;
    Thread shutdownHook;
    Splitter commandSplitter = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings();
    Server server;
    Client client;

    void run() {
        System.out.println(config);
        try {
            console = new ConsoleReader();
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

        boolean halt = false;
        while (!halt) {

            try {
                String line = console.readLine("sdfs> ");
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

        } else if (ImmutableList.of("server", "s").contains(head)) {

            if (tail.size() == 1 && tail.get(0).equals("stop")) {
                if (server == null) {
                    System.out.println("Server not started.");
                } else {
                    System.out.println("Stopping server...");
                    server.stop();
                    server = null;
                    System.out.println("Server stopped.");
                }
            } else if (server != null) {
                System.out.println("Server already started.");
            } else {

                Integer port = null;
                try {
                    port = tail.size() < 1 ? Server.DEFAULT_PORT : Integer.parseInt(tail.get(0));
                } catch (NumberFormatException e) {
                    System.out.println("Port must be an integer.");
                }
                if (port != null) {

                    System.out.println("Starting server on port " + port + "...");

                    String certPath = config.getString("sdfs.cert-path");

                    ProtectedKeyStore protectedKeyStore;
                    // TODO real keystore
                    try {
                        char[] password = "asdfgh".toCharArray();
                        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                        try (InputStream keystoreIn = Files.asByteSource(new File(certPath, "server-keystore")).openBufferedStream()) {
                            keyStore.load(keystoreIn, password);
                        }
                        protectedKeyStore = new ProtectedKeyStore(keyStore, password);
                    } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                        throw new RuntimeException(e); // TODO decent error message
                    }
                    server = new Server(port, protectedKeyStore);
                    server.start();

                    System.out.println("Server started on port " + port + ".");
                }
            }

        } else if (ImmutableList.of("connect", "c").contains(head)) {

            if (client != null) {
                System.out.println("Already connected.");
            } else {

                String host = tail.size() < 1 ? "localhost" : tail.get(0);

                Integer port = null;
                try {
                    port = tail.size() < 2 ? Server.DEFAULT_PORT : Integer.parseInt(tail.get(1));
                } catch (NumberFormatException e) {
                    System.out.println("Port must be an integer.");
                }

                if (port != null) {
                    System.out.println("Connecting to " + host + ":" + port + "...");

                    String certPath = config.getString("sdfs.cert-path");

                    ProtectedKeyStore protectedKeyStore;
                    // TODO real keystore
                    try {
                        char[] password = "asdfgh".toCharArray();
                        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                        try (InputStream keystoreIn = Files.asByteSource(new File(certPath, "client-keystore")).openBufferedStream()) {
                            keyStore.load(keystoreIn, password);
                        }
                        protectedKeyStore = new ProtectedKeyStore(keyStore, password);
                    } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                        throw new RuntimeException(e); // TODO decent error message
                    }
                    client = new Client(host, port, protectedKeyStore);
                    client.connect();

                    System.out.println("Connected to " + host + ":" + port + ".");
                }
            }
        } else if ("disconnect".equals(head)) {
            if (client == null) {
                System.out.println("Not connected.");
            } else {
                System.out.println("Disconnecting...");
                client.disconnect();
                server = null;
                System.out.println("Disconnected.");
            }

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
