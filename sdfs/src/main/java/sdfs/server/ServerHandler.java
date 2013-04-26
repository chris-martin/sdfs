package sdfs.server;

import com.google.common.base.Joiner;
import com.google.common.hash.HashCodes;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;
import sdfs.crypto.CipherStreamFactory;
import sdfs.crypto.UnlockedBlockCipher;
import sdfs.protocol.*;
import sdfs.sdfs.*;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Random;

import static com.google.common.base.Preconditions.checkState;

public class ServerHandler extends SimpleChannelUpstreamHandler {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    private final Random random = new SecureRandom();

    private final Protocol protocol = new Protocol();
    private final SDFS sdfs;
    private final UnlockedBlockCipher fileHashCipher;
    private final CipherStreamFactory cipherStreamFactory;

    private CN client;

    public ServerHandler(SDFS sdfs, UnlockedBlockCipher fileHashCipher, CipherStreamFactory cipherStreamFactory) {
        this.sdfs = sdfs;
        this.fileHashCipher = fileHashCipher;
        this.cipherStreamFactory = cipherStreamFactory;
    }

    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        log.debug("Channel {} connected", ctx.getChannel().getId());

        final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
        if (sslHandler == null) return;

        sslHandler.handshake().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    SSLSession session = sslHandler.getEngine().getSession();
                    checkState(client == null);
                    client = CN.fromLdapPrincipal(session.getPeerPrincipal());
                    log.info("Client `{}' connected.", client.name);
                } else {
                    future.getChannel().close();
                }
            }
        });
    }

    public void messageReceived(final ChannelHandlerContext ctx, MessageEvent event) throws Exception {
        Object msg = event.getMessage();
        if (msg instanceof Header) {
            ((Header) msg).accept(new HeaderHandler(ctx));
        } else {
            super.messageReceived(ctx, event);
        }
    }

    private final class HeaderHandler implements Header.Visitor {
        private final ChannelHandlerContext ctx;
        private HeaderHandler(ChannelHandlerContext ctx) { this.ctx = ctx; }

        public void visit(Header.Bye bye) {
            ctx.getChannel().close();
        }

        public void visit(Header.Prohibited prohibited) {
            throw new ProtocolException("Client cannot sent prohibited header to server");
        }

        public void visit(Header.Unavailable unavailable) {
            throw new ProtocolException("Client cannot sent unavailable header to server");
        }

        @Override
        public void visit(Header.Nonexistent nonexistent) throws Exception {
            throw new ProtocolException("Client cannot sent nonexistent header to server");
        }

        public void visit(Header.Put put) throws IOException {
            log.info("Receiving file `{}' ({} bytes) from {}", put.filename, put.size, client);

            SDFS.Put sdfsPut = null;
            try {
                sdfsPut = sdfs.put(client, put.filename);
            } catch (ResourceUnavailableException e) {
                ctx.getChannel().write(Header.unavailable(put));
            } catch (AccessControlException e) {
                ctx.getChannel().write(Header.prohibited(put));
            }

            OutputStream fileContent;
            if (sdfsPut == null) {
                fileContent = ByteStreams.nullOutputStream();
            } else {
                fileContent = sdfsPut.contentByteSink().openBufferedStream();
                byte[] fileHash = put.hash.asBytes();
                log.debug("File hash {}", BaseEncoding.base16().lowerCase().encode(fileHash));
                fileContent = cipherStreamFactory.encrypt(fileContent, fileHash);
            }

            final InboundFile inboundFile =
                    new InboundFile(fileContent, put.size, protocol.fileHashFunction(), put.hash);

            InboundFileHandler handler = new InboundFileHandler(inboundFile);
            ctx.getPipeline().addBefore("framer", "inboundFile", handler);

            if (sdfsPut != null) {
                handler.transferFuture().addListener(new FinishPut(put, sdfsPut));
            }
        }

        public void visit(Header.Get get) throws IOException {
            SDFS.Get sdfsGet;
            try {
                sdfsGet = sdfs.get(client, get.filename);
            } catch (ResourceNonexistentException e) {
                ctx.getChannel().write(Header.nonexistent(get));
                return;
            } catch (ResourceUnavailableException e) {
                ctx.getChannel().write(Header.unavailable(get));
                return;
            } catch (AccessControlException e) {
                ctx.getChannel().write(Header.prohibited(get));
                return;
            }

            FileMetaData fileMetaData;
            try (InputStream in = sdfsGet.metaByteSource().openBufferedStream()) {
                fileMetaData = FileMetaData.readFrom(in);
            }
            byte[] fileHash = fileHashCipher.decrypt(fileMetaData.encryptedHash);
            log.debug("Recovered file hash {}", BaseEncoding.base16().lowerCase().encode(fileHash));

            log.info("Sending file `{}' ({} bytes) to {}", get.filename, fileMetaData.size, client);

            Header.Put put = new Header.Put();
            put.correlationId = get.correlationId;
            put.filename = get.filename;
            put.hash = HashCodes.fromBytes(fileHash);
            put.size = fileMetaData.size;
            ctx.getChannel().write(put);

            InputStream fileContent = sdfsGet.contentByteSource().openStream();
            fileContent = cipherStreamFactory.decrypt(fileContent, fileHash);

            ctx.getChannel().write(new ChunkedStream(fileContent)).addListener(new FinishGet(fileContent, sdfsGet));
        }

        public void visit(Header.Delegate delegate) {
            log.info("Delegating right {} on `{}' from `{}' to `{}' until {}",
                    Joiner.on(", ").join(delegate.rights), delegate.filename, client, delegate.to, delegate.expiration);

            for (Right right : delegate.rights) {
                try {
                    sdfs.delegate(client, delegate.to, delegate.filename, right, delegate.expiration);
                } catch (AccessControlException e) {
                    ctx.getChannel().write(Header.prohibited(delegate));
                    return;
                } catch (ResourceNonexistentException e) {
                    ctx.getChannel().write(Header.nonexistent(delegate));
                    return;
                }
            }
        }
    }

    private final class FinishPut implements ChannelFutureListener {
        private final Header.Put put;
        private final SDFS.Put sdfsPut;

        private FinishPut(Header.Put put, SDFS.Put sdfsPut) {
            this.put = put;
            this.sdfsPut = sdfsPut;
        }

        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                try {
                    byte[] encryptedFileHash = fileHashCipher.encrypt(put.hash.asBytes());
                    log.debug("Encrypted file hash");

                    FileMetaData metaData = new FileMetaData(put.size, encryptedFileHash);
                    try (OutputStream out = sdfsPut.metaByteSink().openBufferedStream()) {
                        metaData.writeTo(out);
                    }
                } finally {
                    sdfsPut.release();
                }
            } else {
                sdfsPut.abort();
            }
        }
    }

    private static final class FinishGet implements ChannelFutureListener {
        private final InputStream src;
        private final SDFS.Get sdfsGet;

        private FinishGet(InputStream src, SDFS.Get sdfsGet) {
            this.src = src;
            this.sdfsGet = sdfsGet;
        }

        public void operationComplete(ChannelFuture future) throws Exception {
            try {
                src.close();
            } finally {
                sdfsGet.release();
            }
        }
    }

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        log.error("Server error", e.getCause());
        ctx.getChannel().close();
    }

}
