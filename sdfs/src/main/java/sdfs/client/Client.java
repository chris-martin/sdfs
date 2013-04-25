package sdfs.client;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.io.ByteSource;
import com.typesafe.config.Config;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.stream.ChunkedStream;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;
import sdfs.protocol.Header;
import sdfs.protocol.Protocol;
import sdfs.sdfs.Right;
import sdfs.crypto.Crypto;
import sdfs.store.ByteStore;
import sdfs.store.FileStore;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;

import static com.google.common.base.Preconditions.checkState;

public class Client {

    private static final Logger log = LoggerFactory.getLogger(Client.class);

    public final String host;
    public final int port;
    private final Crypto crypto;

    private final ByteStore store;

    private final Protocol protocol = new Protocol();

    private EventLoopGroup group;
    private Channel channel;

    public Client(String host, int port, Crypto crypto, ByteStore store) {
        this.host = host;
        this.port = port;
        this.crypto = crypto;
        this.store = store;
    }

    public static Client fromConfig(Config config) {
        return new Client(
            config.getString("sdfs.host"),
            config.getInt("sdfs.port"),
            new Crypto(config),
            new FileStore(new File(config.getString("sdfs.client-store-path")).toPath())
        );
    }

    public synchronized void connect() throws ConnectException {
        checkState(group == null);

        try {
            group = new NioEventLoopGroup();

            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ClientInitializer(crypto.newSslContext(), store));

            channel = bootstrap.connect(host, port).sync().channel();
        } catch (InterruptedException ignored) {
            disconnect();
        } catch (Throwable e) {
            disconnect();
            Throwables.propagateIfInstanceOf(e, ConnectException.class);
            throw Throwables.propagate(e);
        }
    }

    public void get(String filename) {
        Header.Get get = new Header.Get();
        get.filename = filename;
        channel.write(get);
    }

    public void put(String filename) {
        ByteSource file = store.get(new File(filename).toPath());
        try {
            Header.Put put = new Header.Put();
            put.filename = filename;

            log.debug("Calculating hash");
            Stopwatch stopwatch = new Stopwatch().start();
            put.hash = file.hash(protocol.fileHashFunction());
            log.debug("Hashed file in {}", stopwatch.stop());

            put.size = file.size();
            channel.write(put);

            log.debug("Writing file contents");
            channel.write(new ChunkedStream(file.openBufferedStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void delegate(CN to, String filename, Right right, Duration duration) {
        Header.Delegate delegate = new Header.Delegate();
        delegate.filename = filename;
        delegate.to = to;
        delegate.right = right;
        delegate.expiration = Instant.now().plus(duration); // TODO use Chronos
    }

    public synchronized void disconnect() {
        if (group == null) return;

        if (channel != null) {
            try {
                channel.write(new Header.Bye()).sync();
                channel.closeFuture().sync();
            } catch (InterruptedException ignored) {
            }
        }

        group.shutdown();
        group = null;
    }

    public synchronized String toString() {
        StringBuilder string = new StringBuilder();
        if (group != null) {
            string.append(String.format("Client is connected to %s:%d", host, port));
        } else {
            string.append("Client is disconnected.");
        }
        return string.toString();
    }

}
