package sdfs.protocol;

import com.google.common.io.CountingOutputStream;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundFileHandler extends ChannelInboundByteHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(InboundFileHandler.class);

    private final InboundFile inboundFile;

    public InboundFileHandler(InboundFile inboundFile) {
        this.inboundFile = inboundFile;
    }

    @Override
    protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        CountingOutputStream dest = inboundFile.dest;
        in.readBytes(dest, in.readableBytes());
        log.debug("Received {} bytes of inbound file", dest.getCount());
        if (dest.getCount() == inboundFile.size) {
            log.debug("Finished receiving inbound file");
            ctx.pipeline().remove(this);
            dest.flush();
            dest.close();
        }
    }
}
