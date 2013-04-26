package sdfs.client;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.protocol.*;
import sdfs.store.ByteStore;

import java.io.File;

public class ClientHandler extends SimpleChannelUpstreamHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Protocol protocol = new Protocol();
    private final ByteStore store;

    public ClientHandler(ByteStore store) {
        this.store = store;
    }

    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.debug("Channel {} connected", ctx.getChannel().getId());

        SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
        if (sslHandler == null) return;

        log.debug("SSL handshake");
        sslHandler.handshake();
    }

    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (!(msg instanceof Header)) {
            super.messageReceived(ctx, e);
            return;
        }

        Header header = (Header) msg;

        if (header instanceof Header.Bye) {
            ctx.getChannel().close();
        } else if (header instanceof Header.Put) {
            Header.Put put = (Header.Put) header;
            InboundFile inboundFile = new InboundFile(
                    store.put(new File(put.filename).toPath()).openBufferedStream(),
                    put.size, protocol.fileHashFunction(),
                    put.hash
            );
            ctx.getPipeline().addBefore("framer", "inboundFile", new InboundFileHandler(inboundFile));

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

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        log.error("Client error", e.getCause());
        ctx.getChannel().close();
    }
}
