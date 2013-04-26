package sdfs.protocol;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import sdfs.sdfs.AccessType;
import sdfs.sdfs.DelegationType;
import sdfs.sdfs.Right;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

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
        return "\n-----END HEADER-----";
    }

    public ChannelBuffer headerDelimiter() {
        return headerDelimiter;
    }
    private final ChannelBuffer headerDelimiter = ChannelBuffers.copiedBuffer(endHeader(), headerCharset());

    public int maxHeaderLength() {
        return 65536;
    }

    public Charset headerCharset() {
        return StandardCharsets.UTF_8;
    }

    public String correlationId() {
        return UUID.randomUUID().toString();
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

    public String prohibited() {
        return "prohibited";
    }

    public String unavailable() {
        return "unavailable";
    }

    public String encodeRight(Right right) {
        StringBuilder s = new StringBuilder();
        s.append(encodeAccessType(right.accessType));
        if (right.delegationType == DelegationType.Star) {
            s.append("*");
        }
        return s.toString();
    }

    private String encodeAccessType(AccessType accessType) {
        switch (accessType) {
            case Get: return get();
            case Put: return put();
        }
        throw new ProtocolException("Invalid access type: " + accessType);
    }

    private String star() {
        return "*";
    }

    public Right decodeRight(String s) {
        DelegationType delegationType = DelegationType.None;
        if (s.endsWith(star())) {
            delegationType = DelegationType.Star;
            s = s.substring(0, s.length() - star().length());
        }
        return new Right(decodeAccessType(s), delegationType);
    }

    private AccessType decodeAccessType(String accessType) {
        if (get().equals(accessType)) {
            return AccessType.Get;
        } else if (put().equals(accessType)) {
            return AccessType.Put;
        }
        throw new ProtocolException("Invalid access type: " + accessType);
    }

    public HashFunction fileHashFunction() {
        return Hashing.sha512();
    }

    public BaseEncoding hashEncoding() {
        return BaseEncoding.base16().lowerCase();
    }
}
