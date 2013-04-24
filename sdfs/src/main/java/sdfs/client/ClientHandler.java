package sdfs.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.protocol.*;
import sdfs.store.Store;

public class ClientHandler extends ChannelInboundMessageHandlerAdapter<Header> {

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
    public void messageReceived(ChannelHandlerContext ctx, Header header) throws Exception {
        if (header instanceof Header.Bye) {
            ctx.close();
        } else if (header instanceof Header.Put) {
            Header.Put put = (Header.Put) header;
            InboundFile inboundFile = new InboundFile(
                    store.put(put.filename).openBufferedStream(), put.hash, protocol.fileHashFunction(), put.size);
            ctx.pipeline().addBefore("framer", "inboundFile", new InboundFileHandler(inboundFile));

            log.info("Receiving file `{}' ({} bytes)", put.filename, put.size);
        } else if (header instanceof Header.Prohibited) {
            log.info("Request {} prohibited", header.correlationId);
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
