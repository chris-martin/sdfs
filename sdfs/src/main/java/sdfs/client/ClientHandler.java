package sdfs.client;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CountingOutputStream;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.protocol.FileTransfer;
import sdfs.protocol.Protocol;
import sdfs.store.Store;

import java.util.List;

public class ClientHandler extends ChannelInboundByteHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Store store;

    private final Protocol protocol = new Protocol();

    public ClientHandler(Store store) {
        this.store = store;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("channel {} active", ctx.channel().id());

        ctx.pipeline().get(SslHandler.class).handshake();
    }

    @Override
    protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        FileTransfer fileTransfer = ctx.attr(Protocol.fileTransferAttr).get();
        if (fileTransfer != null) {
            in.readBytes(fileTransfer.dest, in.readableBytes());
            log.debug("Received {} bytes", fileTransfer.dest.getCount());
            if (fileTransfer.dest.getCount() == fileTransfer.size) {
                ctx.attr(Protocol.fileTransferAttr).remove();
                fileTransfer.dest.flush();
                fileTransfer.dest.close();
            }
            return;
        }

        if (in.readableBytes() < protocol.headerLength()) return;

        List<String> headers = protocol.decodeHeaders(in);
        log.info("Received headers\n{}", Joiner.on('\n').join(headers));

        String cmd = headers.get(0);
        List<String> args = headers.size() > 1 ? headers.subList(1, headers.size()) : ImmutableList.<String>of();

        if (cmd.equals(protocol.bye())) {
            ctx.close();
        } else if (cmd.equals(protocol.put())) {
            String filename = args.get(0);
            long size = Long.parseLong(args.get(1));
            ctx.attr(Protocol.fileTransferAttr).set(
                    new FileTransfer(new CountingOutputStream(store.put(filename).openBufferedStream()), size));

            log.info("Receiving file `{}' ({} bytes)", filename, size);
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
