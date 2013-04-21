package sdfs.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import sdfs.ssl.ProtectedKeyStore;

import static com.google.common.base.Preconditions.checkState;

/**
 * SDFS Server.
 */
public class Server {

    private final int port;
    private final ProtectedKeyStore keyStore;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private Thread shutdownHook;

    private boolean started;

    /**
     * @param port port to bind to
     * @param keyStore key store with the server and CA certs.
     */
    public Server(int port, ProtectedKeyStore keyStore) {
        this.port = port;
        this.keyStore = keyStore;
    }

    /**
     * Starts the server in a new thread.
     */
    public synchronized void start() {
        checkState(!started, "Server is already started.");
        started = true;

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                startInCurrentThread();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    void startInCurrentThread() {

        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();

        shutdownHook = new Thread(new Runnable() {
            @Override
            public void run() {
                shutdownHook = null;
                stop();
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerInitializer(keyStore));

            bootstrap.bind(port).sync().channel().closeFuture().sync();
        } catch (InterruptedException ignored) {
        } finally {
            stop();
        }
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

        bossGroup.shutdown();
        workerGroup.shutdown();

        bossGroup = null;
        workerGroup = null;
    }



    public synchronized String toString() {
        StringBuilder string = new StringBuilder();
        if (started) {
            string.append("Server is listening on port " + port);
        } else {
            string.append("Server is not running.");
        }
        return string.toString();
    }

}
