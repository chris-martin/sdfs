package sdfs.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.protocol.*;
import sdfs.store.ByteStore;

import java.io.File;

public class ClientHandler extends ChannelInboundMessageHandlerAdapter<Header> {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Protocol protocol = new Protocol();
    private final ByteStore store;

    public ClientHandler(ByteStore store) {
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
    public void messageReceived(ChannelHandlerContext ctx, Header header) throws Exception {
        if (header instanceof Header.Bye) {
            ctx.close();
        } else if (header instanceof Header.Put) {
            Header.Put put = (Header.Put) header;
            InboundFile inboundFile = new InboundFile(
                    store.put(new File(put.filename).toPath()).openBufferedStream(),
                    put.size, protocol.fileHashFunction(),
                    put.hash
            );
            ctx.pipeline().addBefore("framer", "inboundFile", new InboundFileHandler(inboundFile));

            log.info("Receiving file `{}' ({} bytes)", put.filename, put.size);
        } else if (header instanceof Header.Prohibited) {
            log.info("Request {} re file `{}' prohibited", header.correlationId, ((Header.Prohibited) header).filename);
            // TODO give the client a decent error message
        } else if (header instanceof Header.Unavailable) {
            log.info("File `{}' unavailable", header.correlationId, ((Header.Unavailable) header).filename);
            // TODO give the client a decent error message
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
