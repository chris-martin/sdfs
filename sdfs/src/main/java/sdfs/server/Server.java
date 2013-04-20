package sdfs.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.security.KeyStore;

import static com.google.common.base.Preconditions.checkState;

/**
 * SDFS Server.
 */
public class Server {

    private final int port;
    private final KeyStore keyStore;

    private ServerBootstrap bootstrap;

    /**
     * @param port port to bind to
     * @param keyStore key store with the server and CA certs.
     */
    public Server(int port, KeyStore keyStore) {
        this.port = port;
        this.keyStore = keyStore;
    }

    /**
     * Starts the server and blocks until {@link #stop()} is called.
     */
    public void start() throws InterruptedException {
        checkState(bootstrap == null, "Server is already started.");

        try {
            bootstrap = new ServerBootstrap()
                    .group(new NioEventLoopGroup(), new NioEventLoopGroup())
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerChannelInitializer(keyStore));

            bootstrap.bind(port).sync().channel().closeFuture().sync();
        } finally {
            stop();
        }
    }

    /**
     * Stops the servers.
     */
    public void stop() {
        bootstrap.shutdown();
    }
}
