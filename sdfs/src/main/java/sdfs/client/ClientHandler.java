package sdfs.client;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CountingOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.protocol.InboundFile;
import sdfs.protocol.InboundFileHandler;
import sdfs.protocol.Protocol;
import sdfs.store.Store;

import java.util.List;

public class ClientHandler extends ChannelInboundMessageHandlerAdapter<String> {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Protocol protocol = new Protocol();
    private final Store store;

    public ClientHandler(Store store) {
        this.store = store;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("channel {} active", ctx.channel().id());

        SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        if (sslHandler == null) return;
        sslHandler.handshake();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, String msg) throws Exception {
        List<String> headers = protocol.decodeHeaders(msg);
        log.info("Received headers\n{}", Joiner.on('\n').join(headers));

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
