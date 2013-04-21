package sdfs.protocol;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AttributeKey;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

public class Protocol {

    private static final char HEADER_DELIM = '\n';

    public static final AttributeKey<FileTransfer> fileTransferAttr = new AttributeKey<>("fileTransfer");

    public ByteBuf encodeHeaders(Iterable<String> headers) {
        return encodeHeaders(Joiner.on(HEADER_DELIM).useForNull("").join(headers));
    }

    private ByteBuf encodeHeaders(CharSequence headers) {
        checkArgument(headers.length() <= headerLength());

        return Unpooled.copiedBuffer(Strings.padEnd(headers.toString(), headerLength(), ' '), headerCharset());
    }

    public List<String> decodeHeaders(ByteBuf in) {
        String headers = in.readBytes(headerLength()).toString(headerCharset());
        return ImmutableList.copyOf(Splitter.on(HEADER_DELIM).trimResults().split(headers));
    }

    public int headerLength() {
        return 512;
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
