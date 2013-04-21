package sdfs.client;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.stream.ChunkedStream;
import sdfs.protocol.Protocol;
import sdfs.ssl.ProtectedKeyStore;
import sdfs.store.Store;

import java.io.IOException;
import java.net.ConnectException;

import static com.google.common.base.Preconditions.checkState;

public class Client {

    private final String host;
    private final int port;
    private final ProtectedKeyStore keyStore;

    private final Store store;

    private final Protocol protocol = new Protocol();

    private EventLoopGroup group;
    private Channel channel;

    public Client(String host, int port, ProtectedKeyStore keyStore, Store store) {
        this.host = host;
        this.port = port;
        this.keyStore = keyStore;
        this.store = store;
    }

    public synchronized void connect() throws ConnectException {
        checkState(group == null);

        try {
            group = new NioEventLoopGroup();

            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ClientInitializer(keyStore, store));

            channel = bootstrap.connect(host, port).sync().channel();
        } catch (InterruptedException ignored) {
            disconnect();
        } catch (Throwable e) {
            disconnect();
            Throwables.propagateIfInstanceOf(e, ConnectException.class);
            throw Throwables.propagate(e);
        }
    }

    public synchronized void get(String filename) {
        channel.write(protocol.encodeHeaders(ImmutableList.of("get", filename)));
    }

    public synchronized void put(String filename) {
        ByteSource file = store.get(filename);
        try {
            channel.write(protocol.encodeHeaders(ImmutableList.of("put", filename, String.valueOf(file.size()))));
            channel.write(new ChunkedStream(file.openBufferedStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void disconnect() {
        if (group == null) return;

        if (channel != null) {
            try {
                channel.write(protocol.encodeHeaders(ImmutableList.of(protocol.bye()))).sync();
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
