package sdfs.server;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.CountingOutputStream;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedStream;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;
import sdfs.protocol.FileTransfer;
import sdfs.protocol.Protocol;
import sdfs.store.Store;

import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class ServerHandler extends ChannelInboundByteHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    enum Mode { HEADER, BODY }
    private static final AttributeKey<Mode> modeAttrKey = new AttributeKey<>("mode");
    private static final AttributeKey<OutputStream> putDestKey = new AttributeKey<>("putDest");
    private static final AttributeKey<Long> putRemainingBytes = new AttributeKey<>("putRemainingBytes");

    private final Protocol protocol = new Protocol();
    private final Store store;

    public ServerHandler(Store store) {
        this.store = store;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("channel {} active", ctx.channel().id());

        final SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        sslHandler.handshake().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                SSLSession session = sslHandler.engine().getSession();
                CN client = CN.fromLdapPrincipal(session.getPeerPrincipal());
                log.info("Client `{}' connected.", client);
            }
        });
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
        log.info("received headers\n{}", Joiner.on('\n').join(headers));

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
            // TODO update policy to set client as owner
        } else if (cmd.equals(protocol.get())) {
            String filename = args.get(0);
            ByteSource file = store.get(filename);

            log.info("sending file `{}' ({} bytes)", filename, file.size());

            ctx.write(protocol.encodeHeaders(ImmutableList.of("put", filename, String.valueOf(file.size()))));
            InputStream getSrc = file.openStream();
            ctx.write(new ChunkedStream(getSrc));
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
