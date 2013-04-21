package sdfs.protocol;

import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkArgument;

public class Protocol {

    public ByteBuf encodeHeader(CharSequence header) {
        checkArgument(header.length() <= headerLength());

        return Unpooled.copiedBuffer(Strings.padEnd(header.toString(), headerLength(), ' '), headerCharset());
    }

    public String decodeHeader(ByteBuf in) {
        return in.readBytes(headerLength()).toString(headerCharset()).trim();
    }

    public int headerLength() {
        return 256;
    }

    public Charset headerCharset() {
        return StandardCharsets.UTF_8;
    }

    public String bye() {
        return "bye";
    }
}
