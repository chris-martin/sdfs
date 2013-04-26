package sdfs.client;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.protocol.HeaderCodec;
import sdfs.protocol.HeaderDecoder;
import sdfs.protocol.HeaderEncoder;
import sdfs.protocol.Protocol;
import sdfs.store.ByteStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class ClientPipelineFactory implements ChannelPipelineFactory {

    private static final Logger log = LoggerFactory.getLogger(ClientPipelineFactory.class);

    private final Protocol protocol;
    private final SSLContext sslContext;
    private final ByteStore store;

    public ClientPipelineFactory(Protocol protocol, SSLContext sslContext, ByteStore store) {
        this.protocol = protocol;
        this.sslContext = sslContext;
        this.store = store;
    }

    public ChannelPipeline getPipeline() throws Exception {
        log.debug("Creating client channel pipeline");

        ChannelPipeline pipeline = Channels.pipeline();

        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(true);

        SslHandler sslHandler = new SslHandler(engine);
        sslHandler.setCloseOnSSLException(true);
        pipeline.addLast("ssl", sslHandler);

        pipeline.addLast("chunker", new ChunkedWriteHandler());

        pipeline.addLast("framer",
                new DelimiterBasedFrameDecoder(protocol.maxHeaderLength(), protocol.headerDelimiter()));
        pipeline.addLast("stringDecoder", new StringDecoder(protocol.headerCharset()));
        pipeline.addLast("stringEncoder", new StringEncoder(protocol.headerCharset()));

        HeaderCodec headerCodec = new HeaderCodec(protocol);
        pipeline.addLast("headerDecoder", new HeaderDecoder(headerCodec));
        pipeline.addLast("headerEncoder", new HeaderEncoder(headerCodec));

        pipeline.addLast("client", new ClientHandler(store));

        return pipeline;
    }
}
