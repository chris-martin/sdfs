package sdfs.server;

import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;
import sdfs.protocol.*;
import sdfs.rights.AccessType;
import sdfs.server.policy.PolicyStore;
import sdfs.store.ByteStore;

import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.io.OutputStream;

import static com.google.common.base.Preconditions.checkState;

public class ServerHandler extends ChannelInboundMessageHandlerAdapter<Header> {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    private final Protocol protocol = new Protocol();
    private final ByteStore store;
    private final PolicyStore policyStore;

    private CN client;

    public ServerHandler(ByteStore store, PolicyStore policyStore) {
        this.store = store;
        this.policyStore = policyStore;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("channel {} active", ctx.channel().id());

        final SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        if (sslHandler == null) return;
        sslHandler.handshake().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                SSLSession session = sslHandler.engine().getSession();
                checkState(client == null);
                client = CN.fromLdapPrincipal(session.getPeerPrincipal());
                log.info("Client `{}' connected.", client.name);
            }
        });
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, Header header) throws Exception {
        if (header instanceof Header.Bye) {
            ctx.close();
        } else if (header instanceof Header.Put) {
            Header.Put put = (Header.Put) header;

            log.info("Receiving file `{}' ({} bytes) from {}", put.filename, put.size, client);

            OutputStream out;
            if (!policyStore.hasAccess(client, put.filename, AccessType.Put)) {
                out = ByteStreams.nullOutputStream(); // ignore the actual file contents
                Header.Prohibited prohibited = new Header.Prohibited();
                prohibited.correlationId = header.correlationId;
                ctx.write(prohibited);
            } else {
                policyStore.grantOwner(client, put.filename);
                out = store.put(put.filename).openBufferedStream();
            }

            InboundFile inboundFile =
                    new InboundFile(out, put.hash, protocol.fileHashFunction(), put.size);
            ctx.pipeline().addBefore("framer", "inboundFile", new InboundFileHandler(inboundFile));
        } else if (header instanceof Header.Get) {
            Header.Get get = (Header.Get) header;

            if (!policyStore.hasAccess(client, get.filename, AccessType.Get)) {
                Header.Prohibited prohibited = new Header.Prohibited();
                prohibited.correlationId = header.correlationId;
                ctx.write(prohibited);
                return;
            }

            ByteSource file = store.get(get.filename);

            log.info("Sending file `{}' ({} bytes) to {}", get.filename, file.size(), client);

            Header.Put put = new Header.Put();
            put.correlationId = header.correlationId;
            put.filename = get.filename;
            put.hash = file.hash(protocol.fileHashFunction());
            put.size = file.size();
            ctx.write(put);

            InputStream src = file.openStream();
            ctx.write(new ChunkedStream(src));
        } else if (header instanceof Header.Delegate) {
            Header.Delegate delegate = (Header.Delegate) header;

            log.info("Delegating right {} on `{}' from `{}' to `{}' until {}",
                    delegate.right, delegate.filename, client, delegate.to, delegate.expiration);

            policyStore.delegate(client, delegate.to, delegate.filename, delegate.right, delegate.expiration);
        } else {
            throw new ProtocolException("Invalid header");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            throw cause;
        } catch (Throwable e) {
            log.error("", e);
        }
        ctx.close();
    }

}
