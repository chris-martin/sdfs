package sdfs.server;

import com.google.common.hash.HashCodes;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedStream;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdfs.CN;
import sdfs.crypto.CipherStreamFactory;
import sdfs.crypto.UnlockedBlockCipher;
import sdfs.protocol.*;
import sdfs.sdfs.*;
import sdfs.sdfs.ResourceUnavailableException;
import sdfs.sdfs.SDFS;

import javax.net.ssl.SSLSession;
import java.io.*;

import static com.google.common.base.Preconditions.checkState;

public class ServerHandler extends ChannelInboundMessageHandlerAdapter<Header> {

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

            SDFS.Put sdfsPut = null;
            try {
                sdfsPut = sdfs.put(client, put.filename);
            } catch (ResourceUnavailableException e) {
                ctx.write(Header.unavailable(put));
            } catch (AccessControlException e) {
                ctx.write(Header.prohibited(put));
            }

            OutputStream fileContent;
            if (sdfsPut == null) {
                fileContent = ByteStreams.nullOutputStream();
            } else {
                fileContent = sdfsPut.contentByteSink().openBufferedStream();
                byte[] fileHash = put.hash.asBytes();
                log.debug("File hash {}", BaseEncoding.base16().encode(fileHash));
                fileContent = cipherStreamFactory.encrypt(fileContent, fileHash);
            }

            final InboundFile inboundFile =
                    new InboundFile(fileContent, put.size, protocol.fileHashFunction(), put.hash);

            InboundFileHandler handler = new InboundFileHandler(inboundFile);
            ctx.pipeline().addBefore("framer", "inboundFile", handler);

            if (sdfsPut != null) {
                handler.transferPromise().addListener(new FinishPut(put, sdfsPut));
            }
        } else if (header instanceof Header.Get) {
            Header.Get get = (Header.Get) header;

            SDFS.Get sdfsGet;
            try {
                sdfsGet = sdfs.get(client, get.filename);
            } catch (ResourceUnavailableException e) {
                ctx.write(Header.unavailable(get));
                return;
            } catch (AccessControlException e) {
                ctx.write(Header.prohibited(get));
                return;
            }

            FileMetaData fileMetaData;
            try (InputStream in = sdfsGet.metaByteSource().openBufferedStream()) {
                fileMetaData = FileMetaData.readFrom(in);
            }
            byte[] fileHash = fileHashCipher.decrypt(fileMetaData.encryptedHash);
            log.debug("Recovered file hash {}", BaseEncoding.base16().encode(fileHash));

            log.info("Sending file `{}' ({} bytes) to {}", get.filename, fileMetaData.size, client);

            Header.Put put = new Header.Put();
            put.correlationId = header.correlationId;
            put.filename = get.filename;
            put.hash = HashCodes.fromBytes(fileHash);
            put.size = fileMetaData.size;
            ctx.write(put);

            InputStream fileContent = sdfsGet.contentByteSource().openStream();
            fileContent = cipherStreamFactory.decrypt(fileContent, fileHash);

            ctx.write(new ChunkedStream(fileContent)).addListener(new FinishGet(fileContent, sdfsGet));
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

    private static final class FinishGet implements GenericFutureListener<Future<Void>> {
        private final InputStream src;
        private final SDFS.Get sdfsGet;

        private FinishGet(InputStream src, SDFS.Get sdfsGet) {
            this.src = src;
            this.sdfsGet = sdfsGet;
        }

        public void operationComplete(Future<Void> future) throws Exception {
            try {
                src.close();
            } finally {
                sdfsGet.release();
            }
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
