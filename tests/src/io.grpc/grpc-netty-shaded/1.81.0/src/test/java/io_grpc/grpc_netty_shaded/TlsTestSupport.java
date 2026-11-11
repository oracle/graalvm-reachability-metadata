/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyStore;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.Certificate;

import javax.net.ssl.KeyManagerFactory;

import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator;
import io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.ssl.ApplicationProtocolConfig;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslHandler;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.SelfSignedCertificate;
import io.grpc.netty.shaded.io.netty.util.CharsetUtil;
import io.grpc.netty.shaded.io.netty.util.ReferenceCountUtil;

final class TlsTestSupport {
    private TlsTestSupport() {
    }

    static SslContext context(boolean server, SslProvider implementation, Provider provider) throws Exception {
        SelfSignedCertificate certificate = server ? new SelfSignedCertificate("localhost", "RSA", 2048) : null;
        try {
            SslContextBuilder builder = server
                    ? SslContextBuilder.forServer(certificate.key(), certificate.cert())
                    : SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE);
            if (server && provider != null) {
                char[] password = "test-key".toCharArray();
                KeyStore keys = KeyStore.getInstance("PKCS12");
                keys.load(null, password);
                keys.setKeyEntry("server", certificate.key(), password, new Certificate[]{certificate.cert()});
                KeyManagerFactory keyManagers = KeyManagerFactory.getInstance("X.509", provider);
                keyManagers.init(keys, password);
                builder.keyManager(keyManagers);
            }
            return builder.sslProvider(implementation)
                    .sslContextProvider(provider)
                    .secureRandom(new SecureRandom())
                    .protocols("TLSv1.2")
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            "h2", "http/1.1"))
                    .build();
        } finally {
            if (certificate != null) {
                certificate.delete();
            }
        }
    }

    static void exchange(SslProvider implementation, Provider provider) throws Exception {
        SslContext serverContext = context(true, implementation, provider);
        SslContext clientContext = null;
        EmbeddedChannel server = null;
        EmbeddedChannel client = null;
        try {
            clientContext = context(false, implementation, provider);
            SslHandler serverTls = serverContext.newHandler(ByteBufAllocator.DEFAULT);
            SslHandler clientTls = clientContext.newHandler(ByteBufAllocator.DEFAULT, "localhost", 443);
            serverTls.setHandshakeTimeoutMillis(30000);
            clientTls.setHandshakeTimeoutMillis(30000);
            server = new EmbeddedChannel(serverTls);
            client = new EmbeddedChannel(clientTls);
            for (int i = 0; i < 1000 && (!serverTls.handshakeFuture().isDone()
                    || !clientTls.handshakeFuture().isDone()); i++) {
                transfer(client, server);
                transfer(server, client);
                client.runPendingTasks();
                server.runPendingTasks();
            }
            assertTrue(clientTls.handshakeFuture().isSuccess(), () -> String.valueOf(clientTls.handshakeFuture().cause()));
            assertTrue(serverTls.handshakeFuture().isSuccess(), () -> String.valueOf(serverTls.handshakeFuture().cause()));
            assertEquals("h2", clientTls.applicationProtocol());
            assertEquals("h2", serverTls.applicationProtocol());
            assertTrue(client.writeOutbound(Unpooled.copiedBuffer("encrypted request", CharsetUtil.UTF_8)));
            transfer(client, server);
            ByteBuf request = server.readInbound();
            try {
                assertEquals("encrypted request", request.toString(CharsetUtil.UTF_8));
            } finally {
                request.release();
            }
            assertTrue(server.writeOutbound(Unpooled.copiedBuffer("encrypted reply", CharsetUtil.UTF_8)));
            transfer(server, client);
            ByteBuf reply = client.readInbound();
            try {
                assertEquals("encrypted reply", reply.toString(CharsetUtil.UTF_8));
            } finally {
                reply.release();
            }
        } finally {
            if (client != null) {
                client.finishAndReleaseAll();
            }
            if (server != null) {
                server.finishAndReleaseAll();
            }
            ReferenceCountUtil.release(clientContext);
            ReferenceCountUtil.release(serverContext);
        }
    }

    private static void transfer(EmbeddedChannel source, EmbeddedChannel destination) {
        ByteBuf packet;
        while ((packet = source.readOutbound()) != null) {
            destination.writeInbound(packet);
        }
    }
}
