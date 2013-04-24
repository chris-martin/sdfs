package sdfs.protocol;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.hash.HashCodes;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HeaderCodec extends MessageToMessageCodec<String, Header> {

    private static final Logger log = LoggerFactory.getLogger(HeaderCodec.class);

    private final Protocol protocol = new Protocol();

    private final BiMap<Class<? extends Header>, String> opCodec = ImmutableBiMap.<Class<? extends Header>, String>builder()
            .put(Header.Bye.class, protocol.bye())
            .put(Header.Delegate.class, protocol.delegate())
            .put(Header.Get.class, protocol.get())
            .put(Header.Prohibited.class, protocol.prohibited())
            .put(Header.Put.class, protocol.put())
            .build();

    @Override
    protected Object encode(ChannelHandlerContext ctx, Header msg) throws Exception {
        List<String> headers = new ArrayList<>();
        headers.add(opCodec.get(msg.getClass()));
        headers.add(msg.correlationId == null ? protocol.correlationId() : msg.correlationId.id);

        if (msg instanceof Header.File) {
            headers.add(((Header.File) msg).filename);
        }
        if (msg instanceof Header.Put) {
            headers.add(protocol.hashEncoding().encode(((Header.Put) msg).hash.asBytes()));
            headers.add(String.valueOf(((Header.Put) msg).size));
        }
        if (msg instanceof Header.Delegate) {
            headers.add(((Header.Delegate) msg).to.name);
            headers.add(protocol.encodeRight(((Header.Delegate) msg).right));
            headers.add(String.valueOf(((Header.Delegate) msg).expiration.getMillis()));
        }
        String encoded = protocol.encodeHeaders(headers);
        log.info("encoded headers\n{}\n", encoded);
        return encoded;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, String msg) throws Exception {
        log.info("decoding headers\n{}\n", msg);
        Iterator<String> headers = protocol.decodeHeaders(msg).iterator();

        Header header = opCodec.inverse().get(headers.next()).newInstance();
        header.correlationId = new CorrelationId(headers.next());

        if (header instanceof Header.File) {
            ((Header.File) header).filename = headers.next();
        }
        if (header instanceof Header.Put) {
            ((Header.Put) header).hash = HashCodes.fromBytes(protocol.hashEncoding().decode(headers.next()));
            ((Header.Put) header).size = Long.parseLong(headers.next());
        }
        if (header instanceof Header.Delegate) {
            ((Header.Delegate) header).to = new CN(headers.next());
            ((Header.Delegate) header).right = protocol.decodeRight(headers.next());
            ((Header.Delegate) header).expiration = new Instant(Long.parseLong(headers.next()));
        }

        return header;
    }
}
