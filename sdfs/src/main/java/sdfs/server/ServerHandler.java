package sdfs.server;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashCodes;
import com.google.common.io.ByteSource;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedStream;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;
import sdfs.protocol.InboundFile;
import sdfs.protocol.InboundFileHandler;
import sdfs.protocol.Protocol;
import sdfs.protocol.ProtocolException;
import sdfs.rights.AccessType;
import sdfs.server.policy.PolicyStore;
import sdfs.rights.Right;
import sdfs.store.Store;

import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class ServerHandler extends ChannelInboundMessageHandlerAdapter<String> {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    private final Protocol protocol = new Protocol();
    private final Store store;
    private final PolicyStore policyStore;

    private CN client;

    public ServerHandler(Store store, PolicyStore policyStore) {
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
    public void messageReceived(ChannelHandlerContext ctx, String msg) throws Exception {
        List<String> headersList = protocol.decodeHeaders(msg);
        log.info("received headers\n{}", Joiner.on('\n').join(headersList));

        Iterator<String> headers = headersList.iterator();

        String correlationId = headers.next();
        String cmd = headers.next();

        if (cmd.equals(protocol.bye())) {
            ctx.close();
        } else if (cmd.equals(protocol.put())) {
            String filename = headers.next();

            if (!policyStore.hasAccess(client, filename, AccessType.Put)) {
                ctx.write(protocol.encodeHeaders(ImmutableList.of(
                        correlationId,
                        protocol.prohibited()
                )));
                return;
            }

            HashCode hash = HashCodes.fromBytes(protocol.hashEncoding().decode(headers.next()));
            long size = Long.parseLong(headers.next());

            log.info("Receiving file `{}' ({} bytes) from {}", filename, size, client);

            policyStore.grantOwner(client, filename);

            InboundFile inboundFile =
                    new InboundFile(store.put(filename).openBufferedStream(), hash, protocol.fileHashFunction(), size);
            ctx.pipeline().addBefore("framer", "inboundFile", new InboundFileHandler(inboundFile));
        } else if (cmd.equals(protocol.get())) {
            String filename = headers.next();

            if (!policyStore.hasAccess(client, filename, AccessType.Get)) {
                ctx.write(protocol.encodeHeaders(ImmutableList.of(
                        correlationId,
                        protocol.prohibited()
                )));
                return;
            }

            ByteSource file = store.get(filename);
            HashCode hash = file.hash(protocol.fileHashFunction());

            log.info("Sending file `{}' ({} bytes) to {}", filename, file.size(), client);

            ctx.write(protocol.encodeHeaders(ImmutableList.of(
                    correlationId,
                    protocol.put(),
                    filename,
                    protocol.hashEncoding().encode(hash.asBytes()),
                    String.valueOf(file.size())
            )));
            InputStream src = file.openStream();
            ctx.write(new ChunkedStream(src));
        } else if (cmd.equals(protocol.delegate())) {
            String filename = headers.next();
            CN delegateClient = new CN(headers.next());
            Right right = protocol.decodeRight(headers.next());
            Instant expiration = new Instant(Long.parseLong(headers.next()));

            log.info("Delegating right {} on `{}' from `{}' to `{}' until {}",
                    right, filename, client, delegateClient, expiration.toString());

            policyStore.delegate(client, delegateClient, filename, right, expiration);
        } else {
            throw new ProtocolException("Invalid command: " + cmd);
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
