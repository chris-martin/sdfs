package sdfs.protocol;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class HeaderEncoder extends OneToOneEncoder {

    private static final Logger log = LoggerFactory.getLogger(HeaderEncoder.class);

    private final HeaderCodec codec;

    public HeaderEncoder(HeaderCodec codec) {
        this.codec = codec;
    }

    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (msg instanceof Header) {
            return encode(ctx, channel, (Header) msg);
        }
        return msg;
    }

    protected Object encode(ChannelHandlerContext ctx, Channel channel, Header msg) throws Exception {
        List<String> headers = new ArrayList<>();
        headers.add(codec.opCodec.get(msg.getClass()));
        headers.add(msg.correlationId == null ? codec.protocol.correlationId() : msg.correlationId.id);

        if (msg instanceof Header.File) {
            headers.add(((Header.File) msg).filename);
        }
        if (msg instanceof Header.Put) {
            headers.add(codec.protocol.hashEncoding().encode(((Header.Put) msg).hash.asBytes()));
            headers.add(String.valueOf(((Header.Put) msg).size));
        }
        if (msg instanceof Header.Delegate) {
            headers.add(((Header.Delegate) msg).to.name);
            headers.add(codec.protocol.encodeRight(((Header.Delegate) msg).right));
            headers.add(String.valueOf(((Header.Delegate) msg).expiration.getMillis()));
        }
        String encoded = codec.protocol.encodeHeaders(headers);
        log.info("encoded headers\n{}\n", encoded);
        return encoded;
    }
}
