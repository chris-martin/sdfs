package sdfs.client;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.Output;
import sdfs.protocol.*;
import sdfs.store.ByteStore;

import java.io.File;
import java.io.IOException;

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
        if (msg instanceof Header) {
            ((Header) msg).accept(new HeaderHandler(ctx));
        } else {
            super.messageReceived(ctx, e);
        }
    }

    private final class HeaderHandler implements Header.Visitor {
        private final ChannelHandlerContext ctx;
        private HeaderHandler(ChannelHandlerContext ctx) { this.ctx = ctx; }

        public void visit(Header.Bye bye) {
            ctx.getChannel().close();
        }

        public void visit(final Header.Put put) throws IOException {
            final InboundFile inboundFile = new InboundFile(
                    store.put(new File(put.filename).toPath()).openBufferedStream(),
                    put.size, protocol.fileHashFunction(),
                    put.hash
            );
            InboundFileHandler inboundFileHandler = new InboundFileHandler(inboundFile);
            ctx.getPipeline().addBefore("framer", "inboundFile", inboundFileHandler);
            inboundFileHandler.transferFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        System.out.printf("Got `%s' (%s) in %s (%s).\n",
                                put.filename, Output.transferSize(inboundFile.size),
                                inboundFile.transferTime(), inboundFile.transferRate());
                    } else {
                        System.out.printf("Failed to get `%s'.\n", put.filename);
                    }
                }
            });

            log.info("Receiving file `{}' ({} bytes)", put.filename, put.size);
        }

        public void visit(Header.Prohibited prohibited) {
            System.out.println("`" + prohibited.filename + "' permission denied.");
        }

        public void visit(Header.Unavailable unavailable) {
            System.out.println("`" + unavailable.filename + "' currently unavailable. Please try again.");
        }

        @Override
        public void visit(Header.Nonexistent nonexistent) throws Exception {
            System.out.println("`" + nonexistent.filename + "' does not exist.");
        }

        public void visit(Header.Get get) {
            throw new ProtocolException("Server cannot sent get header to client");
        }

        public void visit(Header.Delegate delegate) {
            throw new ProtocolException("Server cannot sent delegate header to client");
        }
    }

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        log.error("Client error", e.getCause());
        ctx.getChannel().close();
    }
}
