package sdfs;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
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
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
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
                    startedState.shutdownHook = null;
                    stop();
                }
            });
            Runtime.getRuntime().addShutdownHook(startedState.shutdownHook);
        }

        new Thread() {
            public void run() {
                Server server = null;
                Client client = null;

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
                                    continue;
                                }

                                if (server != null) {
                                    System.out.println("Server already started.");
                                    continue;
                                }

                                int port;
                                try {
                                    port = tail.size() < 1 ? Server.DEFAULT_PORT : Integer.parseInt(tail.get(0));
                                } catch (NumberFormatException e) {
                                    System.out.println("Port must be an integer.");
                                    continue;
                                }

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
                                } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
                                    throw new RuntimeException(e); // TODO decent error message
                                }
                                server = new Server(port, protectedKeyStore);
                                server.start();

                                System.out.println("Server started on port " + port + ".");

                            } else if (ImmutableList.of("connect", "c").contains(head)) {

                                if (client != null) {
                                    System.out.println("Already connected.");
                                    continue;
                                }

                                String host = tail.size() < 1 ? "localhost" : tail.get(0);

                                int port;
                                try {
                                    port = tail.size() < 2 ? Server.DEFAULT_PORT : Integer.parseInt(tail.get(1));
                                } catch (NumberFormatException e) {
                                    System.out.println("Port must be an integer.");
                                    continue;
                                }

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
                                } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
                                    throw new RuntimeException(e); // TODO decent error message
                                }
                                client = new Client(host, port, protectedKeyStore);
                                client.connect();

                                System.out.println("Connected to " + host + ":" + port + ".");
                            } else if ("disconnect".equals(head)) {
                                if (client == null) {
                                    System.out.println("Not connected.");
                                } else {
                                    System.out.println("Disconnecting...");
                                    client.disconnect();
                                    client = null;
                                    System.out.println("Disconnected.");
                                }
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
            if (startedState.shutdownHook != null) {
                Runtime.getRuntime().removeShutdownHook(startedState.shutdownHook);
            }
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
