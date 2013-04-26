package sdfs.protocol;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

public class HeaderCodec {

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
                .build();
    }
}
