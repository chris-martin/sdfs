package sdfs.server;

import com.typesafe.config.Config;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import sdfs.crypto.Crypto;
import sdfs.sdfs.SDFS;
import sdfs.sdfs.SDFSImpl;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkState;

/**
 * SDFS Server.
 */
public class Server {

    public final int port;
    private final Crypto crypto;
    private final SDFS sdfs;

    private ServerBootstrap bootstrap;

    private Thread shutdownHook;

    private boolean started;

    public Server(int port, Crypto crypto, SDFS sdfs) {
        this.port = port;
        this.crypto = crypto;
        this.sdfs = sdfs;
    }

    public static Server fromConfig(Config config) {
        return new Server(
            config.getInt("sdfs.port"),
            new Crypto(config),
            SDFSImpl.fromConfig(config)
        );
    }

    /**
     * Starts the server in a new thread.
     */
    public synchronized void start() {
        checkState(!started, "Server is already started.");
        started = true;

        bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool())
        );

        bootstrap.setPipelineFactory(new ServerPipelineFactory(crypto, sdfs));

        shutdownHook = new Thread(new Runnable() {
            @Override
            public void run() {
                shutdownHook = null;
                stop();
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        bootstrap.bind(new InetSocketAddress(port));
    }

    /**
     * Stops the server.
     */
    public synchronized void stop() {
        if (!started) return;
        started = false;

        if (shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null;
        }

        bootstrap.releaseExternalResources();
    }



    public synchronized String toString() {
        StringBuilder string = new StringBuilder();
        if (started) {
            string.append("Server is listening on port ").append(port);
        } else {
            string.append("Server is not running.");
        }
        return string.toString();
    }

}
