package sdfs.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.ServerSocketChannel;

import java.security.KeyStore;

class ServerChannelInitializer extends ChannelInitializer<ServerSocketChannel> {

    private final KeyStore keyStore;

    public ServerChannelInitializer(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @Override
    protected void initChannel(ServerSocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // TODO
    }
}
