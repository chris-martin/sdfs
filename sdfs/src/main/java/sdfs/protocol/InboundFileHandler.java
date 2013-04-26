package sdfs.protocol;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class InboundFileHandler extends SimpleChannelUpstreamHandler implements LifeCycleAwareChannelHandler {

    private static final Logger log = LoggerFactory.getLogger(InboundFileHandler.class);

    private final InboundFile inboundFile;

    private ChannelFuture transferFuture;

    public InboundFileHandler(InboundFile inboundFile) {
        this.inboundFile = inboundFile;
    }

    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        transferFuture = Channels.future(ctx.getChannel());
    }

    public void afterAdd(ChannelHandlerContext ctx) throws Exception { }
    public void beforeRemove(ChannelHandlerContext ctx) throws Exception { }
    public void afterRemove(ChannelHandlerContext ctx) throws Exception { }

    public ChannelFuture transferFuture() {
        return transferFuture;
    }

    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ChannelBuffer buf = (ChannelBuffer) e.getMessage();
        if (inboundFile.read(buf)) {
            log.info("Finished receiving inbound file ({} bytes)", inboundFile.size);
            ctx.getPipeline().remove(this);
            transferFuture.setSuccess();
        }
    }

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        Throwable cause = e.getCause();
        log.error("File transfer error", cause);
        if (transferFuture != null) {
            try {
                inboundFile.close();
            } catch (IOException ignored) {
            }
            transferFuture.setFailure(cause);
        }
        ctx.getChannel().close();
    }
}
