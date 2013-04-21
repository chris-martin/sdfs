package sdfs.client;

import com.google.common.base.Throwables;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import sdfs.protocol.Protocol;
import sdfs.ssl.ProtectedKeyStore;

import java.net.ConnectException;

import static com.google.common.base.Preconditions.checkState;

public class Client {

    private final String host;
    private final int port;
    private final ProtectedKeyStore keyStore;

    private final Protocol protocol = new Protocol();

    private EventLoopGroup group;
    private Channel channel;

    public Client(String host, int port, ProtectedKeyStore keyStore) {
        this.host = host;
        this.port = port;
        this.keyStore = keyStore;
    }

    public synchronized void connect() throws ConnectException {
        checkState(group == null);

        try {
            group = new NioEventLoopGroup();

            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ClientInitializer(keyStore));

            channel = bootstrap.connect(host, port).sync().channel();
        } catch (InterruptedException ignored) {
            disconnect();
        } catch (Throwable e) {
            disconnect();
            Throwables.propagateIfInstanceOf(e, ConnectException.class);
            throw Throwables.propagate(e);
        }
    }

    public synchronized void disconnect() {
        if (group == null) return;

        if (channel != null) {
            try {
                channel.write(protocol.encodeHeader(protocol.bye())).sync().channel().closeFuture().sync();
            } catch (InterruptedException ignored) {
            }
        }

        group.shutdown();
        group = null;
    }
}
