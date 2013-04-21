package sdfs.client;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import sdfs.ssl.ProtectedKeyStore;
import sdfs.ssl.SslContextFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class ClientInitializer extends ChannelInitializer<SocketChannel> {

    private final SSLContext sslContext;

    public ClientInitializer(ProtectedKeyStore keyStore) {
        sslContext = new SslContextFactory().newContext(keyStore);
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(true);

        pipeline.addLast(new SslHandler(engine));
//        pipeline.addLast(new SnappyFramedDecoder());
//        pipeline.addLast(new SnappyFramedEncoder());
        pipeline.addLast(new ClientHandler());
    }
}
