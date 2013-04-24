package sdfs.server;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;
import sdfs.crypto.UnlockedBlockCipher;
import sdfs.protocol.*;
import sdfs.sdfs.AccessControlException;
import sdfs.sdfs.ResourceUnavailableException;
import sdfs.sdfs.SDFS;

import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.io.OutputStream;

import static com.google.common.base.Preconditions.checkState;

public class ServerHandler extends ChannelInboundMessageHandlerAdapter<Header> {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    private final Protocol protocol = new Protocol();
    private final SDFS sdfs;
    private final UnlockedBlockCipher fileHashCipher;

    private CN client;

    public ServerHandler(SDFS sdfs, UnlockedBlockCipher fileHashCipher) {
        this.sdfs = sdfs;
        this.fileHashCipher = fileHashCipher;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("channel {} active", ctx.channel().id());

        final SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
        if (sslHandler == null) return;
        sslHandler.handshake().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                SSLSession session = sslHandler.engine().getSession();
                checkState(client == null);
                client = CN.fromLdapPrincipal(session.getPeerPrincipal());
                log.info("Client `{}' connected.", client.name);
            }
        });
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, Header header) throws Exception {
        if (header instanceof Header.Bye) {
            ctx.close();
        } else if (header instanceof Header.Put) {
            final Header.Put put = (Header.Put) header;

            log.info("Receiving file `{}' ({} bytes) from {}", put.filename, put.size, client);

            SDFS.Put sdfsPut;
            OutputStream out;
            try {
                sdfsPut = sdfs.put(client, put.filename);
                out = sdfsPut.contentByteSink().openBufferedStream();
            } catch (ResourceUnavailableException e) {
                throw e; // todo
            } catch (AccessControlException e) {
                out = ByteStreams.nullOutputStream(); // ignore the actual file contents
                Header.Prohibited prohibited = new Header.Prohibited();
                prohibited.correlationId = header.correlationId;
                ctx.write(prohibited);
            }

            // todo Give the SDFS.Put instance to the InboundFile?
            final InboundFile inboundFile =
                    new InboundFile(out, protocol.fileHashFunction(), put.size);
            InboundFileHandler handler = new InboundFileHandler(inboundFile);
            ctx.pipeline().addBefore("framer", "inboundFile", handler);
            handler.transferDone().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!put.hash.equals(inboundFile.hash())) {
                        log.debug("Hash mismatch:\nheader: {}\nactual: {}", put.hash, inboundFile.hash());
                        throw new ProtocolException("Actual file hash does not match hash in header");
                    }
                    // TODO write key to store
                    byte[] hash = inboundFile.hash().asBytes();
                    byte[] encryptedFileHash = fileHashCipher.encrypt(hash);

                    BaseEncoding base64 = BaseEncoding.base64();
                    log.debug("File hash ({} bytes)\n{}\nencrypted as ({} bytes)\n{}",
                            hash.length,
                            base64.encode(hash),
                            encryptedFileHash.length,
                            base64.encode(encryptedFileHash)
                    );
                }
            });
        } else if (header instanceof Header.Get) {
            Header.Get get = (Header.Get) header;

            SDFS.Get sdfsGet;
            try {
                sdfsGet = sdfs.get(client, get.filename);
            } catch (ResourceUnavailableException e) {
                throw e; // todo
            } catch (AccessControlException e) {
                Header.Prohibited prohibited = new Header.Prohibited();
                prohibited.correlationId = header.correlationId;
                ctx.write(prohibited);
                return;
            }

            ByteSource file = sdfsGet.contentByteSource();

            log.info("Sending file `{}' ({} bytes) to {}", get.filename, file.size(), client);

            Header.Put put = new Header.Put();
            put.correlationId = header.correlationId;
            put.filename = get.filename;
            put.hash = file.hash(protocol.fileHashFunction());
            put.size = file.size();
            ctx.write(put);

            InputStream src = file.openStream();
            ctx.write(new ChunkedStream(src));
        } else if (header instanceof Header.Delegate) {
            Header.Delegate delegate = (Header.Delegate) header;

            log.info("Delegating right {} on `{}' from `{}' to `{}' until {}",
                    delegate.right, delegate.filename, client, delegate.to, delegate.expiration);

            sdfs.delegate(client, delegate.to, delegate.filename, delegate.right, delegate.expiration);
        } else {
            throw new ProtocolException("Invalid header");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            throw cause;
        } catch (Throwable e) {
            log.error("", e);
        }
        ctx.close();
    }

}
