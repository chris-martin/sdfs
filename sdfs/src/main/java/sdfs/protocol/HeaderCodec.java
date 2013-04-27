package sdfs.protocol;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.hash.HashCodes;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HeaderCodec {

    private static final Logger log = LoggerFactory.getLogger(HeaderCodec.class);

    final Protocol protocol;

    final BiMap<Class<? extends Header>, String> opCodec;

    public HeaderCodec(Protocol protocol) {
        this.protocol = protocol;

        opCodec = ImmutableBiMap.<Class<? extends Header>, String>builder()
                .put(Header.Bye.class, protocol.bye())
                .put(Header.Delegate.class, protocol.delegate())
                .put(Header.Get.class, protocol.get())
                .put(Header.Prohibited.class, protocol.prohibited())
                .put(Header.Put.class, protocol.put())
                .put(Header.Unavailable.class, protocol.unavailable())
                .put(Header.Nonexistent.class, protocol.nonexistent())
                .put(Header.Ok.class, protocol.ok())
                .build();
    }

    public Decoder decoder() { return new Decoder(); }
    public Encoder encoder() { return new Encoder(); }

    public class Decoder extends OneToOneDecoder {
        protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
            if (msg instanceof String) {
                return decode(ctx, channel, (String) msg);
            }
            return msg;
        }

        protected Object decode(ChannelHandlerContext ctx, Channel channel, String msg) throws Exception {
            log.info("decoding headers\n{}\n", msg);
            final Iterator<String> headers = protocol.decodeHeaders(msg).iterator();

            Header header = opCodec.inverse().get(headers.next()).newInstance();
            header.correlationId = new CorrelationId(headers.next());

            header.accept(new Header.Visitor() {
                public void visit(Header.Bye bye) { }

                private void visit(Header.File file) {
                    file.filename = headers.next();
                }

                public void visit(Header.Prohibited prohibited) {
                    visit((Header.File) prohibited);
                }

                public void visit(Header.Unavailable unavailable) {
                    visit((Header.File) unavailable);
                }

                public void visit(Header.Nonexistent nonexistent) {
                    visit((Header.File) nonexistent);
                }

                public void visit(Header.Ok ok) {
                    visit((Header.File) ok);
                }

                public void visit(Header.Get get) {
                    visit((Header.File) get);
                }

                public void visit(Header.Put put) {
                    visit((Header.File) put);
                    put.hash = HashCodes.fromBytes(protocol.hashEncoding().decode(headers.next()));
                    put.size = Long.parseLong(headers.next());
                }

                public void visit(Header.Delegate delegate) {
                    visit((Header.File) delegate);
                    delegate.to = new CN(headers.next());
                    delegate.rights = protocol.decodeRights(headers.next());
                    delegate.expiration = new Instant(Long.parseLong(headers.next()));
                }
            });

            return header;
        }
    }

    public class Encoder extends OneToOneEncoder {

        protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
            if (msg instanceof Header) {
                return encode(ctx, channel, (Header) msg);
            }
            return msg;
        }

        protected Object encode(ChannelHandlerContext ctx, Channel channel, Header msg) throws Exception {
            final List<String> headers = new ArrayList<>();
            headers.add(opCodec.get(msg.getClass()));
            headers.add(msg.correlationId == null ? protocol.correlationId() : msg.correlationId.id);

            msg.accept(new Header.Visitor() {
                public void visit(Header.Bye bye) { }

                private void visit(Header.File file) {
                    headers.add(file.filename);
                }

                public void visit(Header.Prohibited prohibited) {
                    visit((Header.File) prohibited);
                }

                public void visit(Header.Unavailable unavailable) {
                    visit((Header.File) unavailable);
                }

                public void visit(Header.Nonexistent nonexistent) {
                    visit((Header.File) nonexistent);
                }

                public void visit(Header.Ok ok) {
                    visit((Header.File) ok);
                }

                public void visit(Header.Get get) {
                    visit((Header.File) get);
                }

                public void visit(Header.Put put) {
                    visit((Header.File) put);
                    headers.add(protocol.hashEncoding().encode(put.hash.asBytes()));
                    headers.add(String.valueOf(put.size));
                }

                public void visit(Header.Delegate delegate) {
                    visit((Header.File) delegate);
                    headers.add(delegate.to.name);
                    headers.add(protocol.encodeRights(delegate.rights));
                    headers.add(String.valueOf(delegate.expiration.getMillis()));
                }
            });

            String encoded = protocol.encodeHeaders(headers);
            log.info("encoded headers\n{}\n", encoded);
            return encoded;
        }
    }
}
