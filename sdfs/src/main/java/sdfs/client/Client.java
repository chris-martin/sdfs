package sdfs.client;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.typesafe.config.Config;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.stream.ChunkedStream;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;
import sdfs.Output;
import sdfs.crypto.Crypto;
import sdfs.protocol.Header;
import sdfs.protocol.Protocol;
import sdfs.sdfs.Right;
import sdfs.store.ByteStore;
import sdfs.store.FileStore;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkState;

public class Client {

    private static final Logger log = LoggerFactory.getLogger(Client.class);

    public final InetSocketAddress serverAddr;
    private final Crypto crypto;
    private final ByteStore store;

    private final Protocol protocol = new Protocol();

    private ClientBootstrap bootstrap;
    private Channel channel;

    public Client(String host, int port, Crypto crypto, ByteStore store) {
        serverAddr = new InetSocketAddress(host, port);
        this.crypto = crypto;
        this.store = store;
    }

    public static Client fromConfig(Config config) {
        return new Client(
            config.getString("sdfs.host"),
            config.getInt("sdfs.port"),
            new Crypto(config),
            new FileStore(new File(config.getString("sdfs.store.client")).toPath())
        );
    }

    public synchronized void connect(final Runnable onClose) throws ConnectException {
        checkState(bootstrap == null);

        try {
            bootstrap = new ClientBootstrap(
                    new NioClientSocketChannelFactory(
                            Executors.newCachedThreadPool(),
                            Executors.newCachedThreadPool()));

            bootstrap.setPipelineFactory(new ClientPipelineFactory(protocol, crypto.newSslContext(), store));

            channel = bootstrap.connect(serverAddr).sync().getChannel();

            channel.getCloseFuture().addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    channel = null;
                    onClose.run();

                    // Netty requires releasing of resources to not happen in an I/O thread
                    new Thread(new Runnable() {
                        public void run() {
                            releaseResources();
                        }
                    }).start();
                }
            });
        } catch (InterruptedException ignored) {
            disconnect();
        } catch (Throwable e) {
            disconnect();
            Throwables.propagateIfInstanceOf(e, ConnectException.class);
            throw Throwables.propagate(e);
        }
    }

    public synchronized void get(String filename) {
        checkState(channel != null);

        Header.Get get = new Header.Get();
        get.filename = filename;
        channel.write(get);
    }

    public synchronized void put(String filename) {
        checkState(channel != null);

        ByteSource file = store.get(new File(filename).toPath());
        try {
            final Header.Put put = new Header.Put();
            put.filename = filename;

            log.debug("Calculating hash");
            final Stopwatch stopwatch = new Stopwatch().start();
            put.hash = file.hash(protocol.fileHashFunction());
            log.debug("Hashed file in {}", stopwatch.stop());

            put.size = file.size();
            channel.write(put);

            stopwatch.reset().start();
            log.debug("Writing file contents");
            channel.write(new ChunkedStream(file.openBufferedStream())).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    stopwatch.stop();
                    if (future.isSuccess()) {
                        System.out.printf("Put `%s' (%s) in %s (%s).%n",
                                put.filename, Output.transferSize(put.size),
                                stopwatch.toString(), Output.transferRate(put.size, stopwatch));
                    } else {
                        System.out.printf("Failed to put `%s'.%n", put.filename);
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void delegate(CN to, String filename, Iterable<Right> rights, Instant expiration) {
        checkState(channel != null);

        Header.Delegate delegate = new Header.Delegate();
        delegate.filename = filename;
        delegate.to = to;
        delegate.rights = ImmutableList.copyOf(rights);
        delegate.expiration = expiration;

        channel.write(delegate);
    }

    public synchronized void disconnect() {
        if (channel != null) {
            try {
                channel.write(new Header.Bye()).sync().getChannel().getCloseFuture().sync();
            } catch (Exception e) {
                log.debug("Could not close connection gracefully", e);
            }
            channel = null;
        }

        releaseResources();
    }

    private synchronized void releaseResources() {
        if (bootstrap != null) {
            bootstrap.releaseExternalResources();
            bootstrap = null;
        }
    }

    public synchronized String toString() {
        StringBuilder string = new StringBuilder();
        if (bootstrap != null) {
            string.append(String.format("Client is connected to %s", serverAddr.toString()));
        } else {
            string.append("Client is disconnected.");
        }
        return string.toString();
    }

}
