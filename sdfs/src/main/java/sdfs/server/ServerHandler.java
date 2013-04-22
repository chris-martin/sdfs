package sdfs.server;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.CountingOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;
import sdfs.protocol.InboundFile;
import sdfs.protocol.InboundFileHandler;
import sdfs.protocol.Protocol;
import sdfs.store.Store;

import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class ServerHandler extends ChannelInboundMessageHandlerAdapter<String> {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    private final Protocol protocol = new Protocol();
    private final Store store;

    private CN client;

    public ServerHandler(Store store) {
        this.store = store;
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
        List<String> headers = protocol.decodeHeaders(msg);
        log.info("received headers\n{}", Joiner.on('\n').join(headers));

        String cmd = headers.get(0);
        List<String> args = headers.size() > 1 ? headers.subList(1, headers.size()) : ImmutableList.<String>of();

        if (cmd.equals(protocol.bye())) {
            ctx.close();
        } else if (cmd.equals(protocol.put())) {
            String filename = args.get(0);
            long size = Long.parseLong(args.get(1));

            InboundFile inboundFile =
                    new InboundFile(new CountingOutputStream(store.put(filename).openBufferedStream()), size);
            ctx.pipeline().addBefore("framer", "inboundFile", new InboundFileHandler(inboundFile));

            log.info("Receiving file `{}' ({} bytes)", filename, size);
            // TODO update policy to set client as owner
        } else if (cmd.equals(protocol.get())) {
            String filename = args.get(0);
            ByteSource file = store.get(filename);

            log.info("Sending file `{}' ({} bytes)", filename, file.size());

            ctx.write(protocol.encodeHeaders(ImmutableList.of("put", filename, String.valueOf(file.size()))));
            InputStream getSrc = file.openStream();
            ctx.write(new ChunkedStream(getSrc));
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
