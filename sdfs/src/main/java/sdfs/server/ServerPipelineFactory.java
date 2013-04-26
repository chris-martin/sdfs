package sdfs.server;

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
import sdfs.crypto.CipherStreamFactory;
import sdfs.crypto.Crypto;
import sdfs.crypto.UnlockedBlockCipher;
import sdfs.protocol.HeaderCodec;
import sdfs.protocol.Protocol;
import sdfs.sdfs.SDFS;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

class ServerPipelineFactory implements ChannelPipelineFactory {

    private static final Logger log = LoggerFactory.getLogger(ServerPipelineFactory.class);

    private final SSLContext sslContext;
    private final SDFS sdfs;
    private final UnlockedBlockCipher fileHashCipher;
    private final CipherStreamFactory cipherStreamFactory;

    private final Protocol protocol = new Protocol();

    public ServerPipelineFactory(Crypto crypto, SDFS sdfs) {
        sslContext = crypto.newSslContext();
        fileHashCipher = crypto.unlockedBlockCipher();
        cipherStreamFactory = new CipherStreamFactory(crypto);
        this.sdfs = sdfs;
    }

    public ChannelPipeline getPipeline() throws Exception {
        log.debug("Creating server channel pipeline");

        ChannelPipeline pipeline = Channels.pipeline();

        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(true);

        SslHandler sslHandler = new SslHandler(engine);
        sslHandler.setCloseOnSSLException(true);
        pipeline.addLast("ssl", sslHandler);

        pipeline.addLast("chunker", new ChunkedWriteHandler());

        pipeline.addLast("framer",
                new DelimiterBasedFrameDecoder(protocol.maxHeaderLength(), protocol.headerDelimiter()));
        pipeline.addLast("stringDecoder", new StringDecoder(protocol.headerCharset()));
        pipeline.addLast("stringEncoder", new StringEncoder(protocol.headerCharset()));

        HeaderCodec headerCodec = new HeaderCodec(protocol);
        pipeline.addLast("headerDecoder", headerCodec.decoder());
        pipeline.addLast("headerEncoder", headerCodec.encoder());

        pipeline.addLast("server", new ServerHandler(sdfs, fileHashCipher, cipherStreamFactory));

        return pipeline;
    }
}
