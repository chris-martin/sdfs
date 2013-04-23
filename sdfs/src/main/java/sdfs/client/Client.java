package sdfs.client;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.io.ByteSource;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.stream.ChunkedStream;
import org.joda.time.Duration;
import org.joda.time.Instant;
import sdfs.CN;
import sdfs.protocol.Protocol;
import sdfs.server.policy.Right;
import sdfs.ssl.SslContextFactory;
import sdfs.store.Store;

import java.io.IOException;
import java.net.ConnectException;

import static com.google.common.base.Preconditions.checkState;

public class Client {

    private final String host;
    private final int port;
    private final SslContextFactory sslContextFactory;

    private final Store store;

    private final Protocol protocol = new Protocol();

    private EventLoopGroup group;
    private Channel channel;

    public Client(String host, int port, SslContextFactory sslContextFactory, Store store) {
        this.host = host;
        this.port = port;
        this.sslContextFactory = sslContextFactory;
        this.store = store;
    }

    public synchronized void connect() throws ConnectException {
        checkState(group == null);

        try {
            group = new NioEventLoopGroup();

            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ClientInitializer(sslContextFactory.newContext(), store));

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
        channel.write(protocol.encodeHeaders(ImmutableList.of(
                protocol.correlationId(),
                protocol.get(),
                filename
        )));
    }

    public void put(String filename) {
        ByteSource file = store.get(filename);
        try {
            HashCode hash = file.hash(protocol.fileHashFunction());
            channel.write(protocol.encodeHeaders(ImmutableList.of(
                    protocol.correlationId(),
                    protocol.put(),
                    filename,
                    protocol.hashEncoding().encode(hash.asBytes()),
                    String.valueOf(file.size())
            )));
            channel.write(new ChunkedStream(file.openBufferedStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void delegate(CN to, String filename, Right right, Duration duration) {
        channel.write(protocol.encodeHeaders(ImmutableList.of(
                protocol.correlationId(),
                protocol.delegate(),
                filename,
                to.name,
                protocol.encodeRight(right),
                String.valueOf(Instant.now().plus(duration).getMillis()) // TODO use Chronos
        )));
    }

    public synchronized void disconnect() {
        if (group == null) return;

        if (channel != null) {
            try {
                channel.write(protocol.encodeHeaders(ImmutableList.of(
                        protocol.correlationId(),
                        protocol.bye()
                ))).sync();
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
