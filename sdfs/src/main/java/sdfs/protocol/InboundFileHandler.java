package sdfs.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

public class InboundFileHandler extends ChannelInboundByteHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(InboundFileHandler.class);

    private final InboundFile inboundFile;

    private ChannelPromise transferPromise;

    public InboundFileHandler(InboundFile inboundFile) {
        this.inboundFile = inboundFile;
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        transferPromise = ctx.newPromise();
    }

    public ChannelFuture transferPromise() {
        return transferPromise;
    }

    @Override
    protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (inboundFile.read(in)) {
            log.info("Finished receiving inbound file ({} bytes)", inboundFile.size);
            ctx.pipeline().remove(this);
            transferPromise.trySuccess();
        }
    }

    private void fail(ChannelHandlerContext ctx, Throwable cause) {
        log.error("File transfer error", cause);
        if (transferPromise != null) {
            try {
                inboundFile.close();
            } catch (IOException ignored) {
            }
            transferPromise.tryFailure(cause);
        }
        ctx.close();
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        fail(ctx, cause);
    }

    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        fail(ctx, null);
    }
}
