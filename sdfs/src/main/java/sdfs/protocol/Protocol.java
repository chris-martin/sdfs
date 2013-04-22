package sdfs.protocol;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Protocol {

    private static final char HEADER_DELIM = '\n';

    public String encodeHeaders(Iterable<String> headers) {
        StringBuilder s = new StringBuilder();
        Joiner.on(HEADER_DELIM).useForNull("").appendTo(s, headers);
        s.append(endHeader());
        return s.toString();
    }

    public List<String> decodeHeaders(String headers) {
        return ImmutableList.copyOf(Splitter.on(HEADER_DELIM).trimResults().split(headers));
    }

    private String endHeader() {
        return "-----END HEADER-----";
    }

    public ByteBuf headerDelimiter() {
        return Unpooled.copiedBuffer(endHeader(), headerCharset());
    }

    public int maxHeaderLength() {
        return 65536;
    }

    public Charset headerCharset() {
        return StandardCharsets.UTF_8;
    }

    public String bye() {
        return "bye";
    }

    public String put() {
        return "put";
    }

    public String get() {
        return "get";
    }

    public String delegate() {
        return "delegate";
    }

    public String delegateStar() {
        return "delegate*";
    }
}
