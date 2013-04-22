package sdfs.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.store.Store;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class ClientInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger log = LoggerFactory.getLogger(ClientInitializer.class);

    private final SSLContext sslContext;
    private final Store store;

    public ClientInitializer(SSLContext sslContext, Store store) {
        this.sslContext = sslContext;
        this.store = store;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(true);

        pipeline.addLast(new SslHandler(engine));
//        pipeline.addLast(new SnappyFramedDecoder());
//        pipeline.addLast(new SnappyFramedEncoder());
        pipeline.addLast(new ChunkedWriteHandler());
        pipeline.addLast(new ClientHandler(store));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            throw cause;
        } catch (Throwable e) {
            log.error("Could not initialize client channel.", e);
        }
        ctx.close();
    }
}
