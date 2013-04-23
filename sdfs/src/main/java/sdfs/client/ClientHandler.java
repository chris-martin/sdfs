package sdfs.client;

import com.google.common.base.Joiner;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashCodes;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.protocol.InboundFile;
import sdfs.protocol.InboundFileHandler;
import sdfs.protocol.Protocol;
import sdfs.protocol.ProtocolException;
import sdfs.store.Store;

import java.util.Iterator;
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
        List<String> headersList = protocol.decodeHeaders(msg);
        log.info("received headers\n{}", Joiner.on('\n').join(headersList));

        Iterator<String> headers = headersList.iterator();

        String correlationId = headers.next();
        String cmd = headers.next();

        if (cmd.equals(protocol.bye())) {
            ctx.close();
        } else if (cmd.equals(protocol.put())) {
            String filename = headers.next();
            HashCode hash = HashCodes.fromBytes(protocol.hashEncoding().decode(headers.next()));
            long size = Long.parseLong(headers.next());

            InboundFile inboundFile =
                    new InboundFile(store.put(filename).openBufferedStream(), hash, protocol.fileHashFunction(), size);
            ctx.pipeline().addBefore("framer", "inboundFile", new InboundFileHandler(inboundFile));

            log.info("Receiving file `{}' ({} bytes)", filename, size);
        } else if (cmd.equals(protocol.prohibited())) {
            log.info("Request {} prohibited", correlationId);
            // TODO fix for put
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
