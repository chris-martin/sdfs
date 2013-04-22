package sdfs.protocol;

import com.google.common.io.CountingOutputStream;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;

public class InboundFileHandler extends ChannelInboundByteHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(InboundFileHandler.class);

    private final InboundFile inboundFile;

    public InboundFileHandler(InboundFile inboundFile) {
        this.inboundFile = inboundFile;
    }

    @Override
    protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        OutputStream dest = inboundFile.dest();
        in.readBytes(dest, in.readableBytes());
        log.debug("Received {} bytes of inbound file", inboundFile.count());
        if (inboundFile.count() == inboundFile.size) {
            log.debug("Finished receiving inbound file ({} bytes)", inboundFile.count());
            ctx.pipeline().remove(this);
            log.info("Hash {}match", inboundFile.hashMatches() ? "" : "mis");
            // TODO retry on mismatch ?
            dest.flush();
            dest.close();
        }
    }
}
