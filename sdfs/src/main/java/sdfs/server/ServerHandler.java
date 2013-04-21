package sdfs.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.protocol.Protocol;

public class ServerHandler extends ChannelInboundByteHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    enum Mode { HEADER, CONTENT }

    private Mode mode = Mode.HEADER;

    private final Protocol protocol = new Protocol();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("channel {} active", ctx.channel().id());
    }

    @Override
    protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (in.readableBytes() < protocol.headerLength()) return;

        String header = protocol.decodeHeader(in);
        log.info("received header `{}'", header);

        if (header.equals(protocol.bye())) {
            ctx.close();
        }
    }
}
