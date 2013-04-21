package sdfs.server;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.protocol.Protocol;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

public class ServerHandler extends ChannelInboundByteHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    enum Mode { HEADER, CONTENT }

    private Mode mode = Mode.HEADER;

    private final Protocol protocol = new Protocol();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("channel {} active", ctx.channel().id());

        final SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        sslHandler.handshake().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                SSLSession session = sslHandler.engine().getSession();
                String client = getPeerCn(session);
                log.info("Client `{}' connected.", client);
            }
        });
    }

    private String getPeerCn(SSLSession session) throws InvalidNameException, SSLPeerUnverifiedException {
        return FluentIterable
                .from(new LdapName(session.getPeerPrincipal().getName()).getRdns())
                .firstMatch(new Predicate<Rdn>() {
                    @Override
                    public boolean apply(Rdn input) {
                        return input.getType().equalsIgnoreCase("CN");
                    }
                })
                .get().getValue().toString();
    }

    @Override
    protected void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (in.readableBytes() < protocol.headerLength()) return;

        String header = protocol.decodeHeader(in);
        log.info("received header `{}'", header);

        if (header.equals(protocol.bye())) {
            ctx.close();
            return;
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
