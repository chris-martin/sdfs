package sdfs.client;

import com.google.common.base.Stopwatch;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.Output;
import sdfs.protocol.*;
import sdfs.store.ByteStore;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class ClientHandler extends SimpleChannelUpstreamHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Protocol protocol = new Protocol();
    private final ByteStore store;

    private final AtomicReference<OutboundFile> outboundFile = new AtomicReference<>();

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
                        System.out.printf("Got `%s' (%s) in %s (%s).%n",
                                put.filename, Output.transferSize(inboundFile.size),
                                inboundFile.transferTime(), inboundFile.transferRate());
                    } else {
                        System.out.printf("Failed to get `%s'.%n", put.filename);
                    }
                }
            });

            log.info("Receiving file `{}' ({} bytes)", put.filename, put.size);
        }

        public void visit(Header.Ok ok) throws IOException {
            final OutboundFile file = outboundFile.get();
            if (file == null) {
                throw new ProtocolException("Server OK'd client put, but client didn't put");
            }
            log.debug("Server OK'd put of `{}'. Writing file contents...", file.put.filename);

            final Stopwatch stopwatch = new Stopwatch().start();
            ctx.getChannel().write(new ChunkedStream(file.file.openBufferedStream())).addListener(
                    new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            stopwatch.stop();
                            outboundFile.compareAndSet(file, null);
                            Header.Put put = file.put;
                            if (future.isSuccess()) {
                                System.out.printf("Put `%s' (%s) in %s (%s).%n",
                                        put.filename, Output.transferSize(put.size),
                                        stopwatch.toString(), Output.transferRate(put.size, stopwatch));
                            } else {
                                System.out.printf("Failed to put `%s'.%n", put.filename);
                            }
                        }
                    });
        }

        public void visit(Header.Prohibited prohibited) {
            outboundFile.set(null);
            System.out.println("`" + prohibited.filename + "' permission denied.");
        }

        public void visit(Header.Unavailable unavailable) {
            outboundFile.set(null);
            System.out.println("`" + unavailable.filename + "' currently unavailable. Please try again.");
        }

        @Override
        public void visit(Header.Nonexistent nonexistent) {
            outboundFile.set(null);
            System.out.println("`" + nonexistent.filename + "' does not exist.");
        }

        public void visit(Header.Get get) {
            throw new ProtocolException("Server cannot sent get header to client");
        }

        public void visit(Header.Delegate delegate) {
            throw new ProtocolException("Server cannot sent delegate header to client");
        }
    }

    boolean setOutboundFile(OutboundFile outboundFile) {
        return this.outboundFile.compareAndSet(null, outboundFile);
    }

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        log.error("Client error", e.getCause());
        ctx.getChannel().close();
    }
}
