package sdfs.protocol;

import com.google.common.hash.HashCodes;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;

import java.util.Iterator;

public class HeaderDecoder extends OneToOneDecoder {

    private static final Logger log = LoggerFactory.getLogger(HeaderEncoder.class);

    private final HeaderCodec codec;

    public HeaderDecoder(HeaderCodec codec) {
        this.codec = codec;
    }

    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (msg instanceof String) {
            return decode(ctx, channel, (String) msg);
        }
        return msg;
    }

    protected Object decode(ChannelHandlerContext ctx, Channel channel, String msg) throws Exception {
        log.info("decoding headers\n{}\n", msg);
        Iterator<String> headers = codec.protocol.decodeHeaders(msg).iterator();

        Header header = codec.opCodec.inverse().get(headers.next()).newInstance();
        header.correlationId = new CorrelationId(headers.next());

        if (header instanceof Header.File) {
            ((Header.File) header).filename = headers.next();
        }
        if (header instanceof Header.Put) {
            ((Header.Put) header).hash = HashCodes.fromBytes(codec.protocol.hashEncoding().decode(headers.next()));
            ((Header.Put) header).size = Long.parseLong(headers.next());
        }
        if (header instanceof Header.Delegate) {
            ((Header.Delegate) header).to = new CN(headers.next());
            ((Header.Delegate) header).rights = codec.protocol.decodeRights(headers.next());
            ((Header.Delegate) header).expiration = new Instant(Long.parseLong(headers.next()));
        }

        return header;
    }
}
