package sdfs.server;

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
import sdfs.sdfs.AccessControlException;
import sdfs.sdfs.ResourceUnavailableException;
import sdfs.sdfs.SDFS;

import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.io.OutputStream;

import static com.google.common.base.Preconditions.checkState;

public class ServerHandler extends SimpleChannelUpstreamHandler {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

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

    public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {
        Object msg = event.getMessage();
        if (!(msg instanceof Header)) {
            super.messageReceived(ctx, event);
            return;
        }

        Header header = (Header) msg;

        if (header instanceof Header.Bye) {
            ctx.getChannel().close();
        } else if (header instanceof Header.Put) {
            final Header.Put put = (Header.Put) header;

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
        } else if (header instanceof Header.Get) {
            Header.Get get = (Header.Get) header;

            SDFS.Get sdfsGet;
            try {
                sdfsGet = sdfs.get(client, get.filename);
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
            put.correlationId = header.correlationId;
            put.filename = get.filename;
            put.hash = HashCodes.fromBytes(fileHash);
            put.size = fileMetaData.size;
            ctx.getChannel().write(put);

            InputStream fileContent = sdfsGet.contentByteSource().openStream();
            fileContent = cipherStreamFactory.decrypt(fileContent, fileHash);

            ctx.getChannel().write(new ChunkedStream(fileContent)).addListener(new FinishGet(fileContent, sdfsGet));
        } else if (header instanceof Header.Delegate) {
            Header.Delegate delegate = (Header.Delegate) header;

            log.info("Delegating right {} on `{}' from `{}' to `{}' until {}",
                    delegate.right, delegate.filename, client, delegate.to, delegate.expiration);

            sdfs.delegate(client, delegate.to, delegate.filename, delegate.right, delegate.expiration);
        } else {
            throw new ProtocolException("Invalid header");
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
