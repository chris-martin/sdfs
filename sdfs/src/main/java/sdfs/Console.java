package sdfs;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.tools.jline.console.ConsoleReader;
import sdfs.client.Client;
import sdfs.server.Server;
import sdfs.ssl.ProtectedKeyStore;
import sdfs.ssl.SslContextFactory;
import sdfs.store.SimpleStore;
import sdfs.store.Store;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

public class Console {

    private static final Logger log = LoggerFactory.getLogger(Console.class);

    Config config = ConfigFactory.empty();
    ConsoleReader console;
    Thread shutdownHook;
    Splitter commandSplitter = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings();
    Server server;
    Client client;
    IdentityCollection certs;
    Store serverStore;
    Store clientStore;
    boolean halt;

    void run() {
        try {
            console = new ConsoleReader();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        shutdownHook = new Thread(new Runnable() {
            public void run() {
                synchronized(Console.this) {
                    shutdownHook = null;
                    stop();
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        while (!halt) {

            String line;
            try {
                line = console.readLine("sdfs> ");
            } catch (IOException e) {
                halt = true;
                continue;
            }

            execute(ImmutableList.copyOf(commandSplitter.split(line)));
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

            str.append(
                CharStreams.toString(new InputStreamReader(
                    Resources.getResource("help.txt").openStream())
                )
            ).append("\n");

        } catch (IOException e) {
            str.append("Error: " + e.getMessage()).append("\n");
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

    String certs() {

        StringBuilder str = new StringBuilder();

        String c = certs == null ? "" : certs.toString();

        str.append("\nCertificates:\n\n").append(
            c.replaceAll("(.+)\n", "    $1\n")
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
            System.out.println(certs());

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

        } else if ("cert".equals(head)) {

            if (tail.size() == 0) {
                System.out.println(certs());
            } else if (tail.get(0).equals("load")) {
                loadCerts();
            }

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
                try {
                    int port = getPort();
                    System.out.println("Starting server on port " + port + "...");
                    server = new Server(port, sslContextFactory("server"), serverStore);
                    server.start();
                    System.out.println("Server started on port " + port + ".");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
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
                    String host = tail.size() < 1 ? "localhost" : tail.get(0);
                    Integer port = getPort();
                    System.out.println("Connecting to " + host + ":" + port + "...");
                    client = new Client(host, port, sslContextFactory("client"), clientStore);
                    client.connect();
                    System.out.println("Connected to " + host + ":" + port + ".");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
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
            }
        }

    }

    SslContextFactory sslContextFactory(String identity) {
        return new SslContextFactory(keyStore(identity), caCertsKeyStore());
    }

    KeyStore caCertsKeyStore() {
        Config keyStoreConfig = config.getConfig("sdfs.ca-certs-keystore");
        File file = new File(keyStoreConfig.getString("path"));
        char[] password = keyStoreConfig.getString("password").toCharArray();
        try (FileInputStream in = new FileInputStream(file)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(in, password);
            return keyStore;
        } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException e) {
            log.error("", e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    ProtectedKeyStore keyStore(String identity) {
        if (true) {
            CN cn = new CN(config.getConfig("sdfs.identity").getString(identity));
            try {
                File keystoreFile = new File(config.getString("sdfs.cert-path"), cn.name + ".p12");
                char[] keystorePassword = "storepass".toCharArray(); // TODO
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (FileInputStream in = new FileInputStream(keystoreFile)) {
                    keyStore.load(in, keystorePassword);
                }
                return new ProtectedKeyStore(keyStore, "keypass".toCharArray()); // TODO
            } catch (Exception e) {
                log.error("", e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        // TODO
        try {
            ProtectedKeyStore protectedKeyStore = ProtectedKeyStore.createEmpty(); // TODO
            CN cn = new CN(config.getConfig("sdfs.identity").getString(identity));
            IdentityCollection.Identity cert = certs.byCN.get(cn);
            if (cert == null) {
                throw new RuntimeException("No such cert: " + cn);
            }
            X509Certificate x509 = cert.x509;
            protectedKeyStore.keyStore.setCertificateEntry(identity, x509);
            protectedKeyStore.keyStore.setKeyEntry(identity, cert.key.getEncoded(), new Certificate[]{x509});
            return protectedKeyStore;
        } catch (KeyStoreException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    int getPort() {
        try {
            return config.getInt("sdfs.port");
        } catch (ConfigException.Missing e) {
            throw new RuntimeException("Port is not specified.", e);
        } catch (ConfigException.BadValue e) {
            throw new RuntimeException("Port must be an integer.", e);
        }
    }

    void loadCerts() {
        certs = new IdentityCollection();
        certs.load(new File(config.getString("sdfs.cert-path")));
    }

    void initStores() {
        serverStore = new SimpleStore(new File(config.getString("sdfs.server-store-path")));
        clientStore = new SimpleStore(new File(config.getString("sdfs.client-store-path")));
    }

    public static void main(String[] args) {
        Console console = new Console();
        console.config = config(args);
        console.loadCerts();
        console.initStores();
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
