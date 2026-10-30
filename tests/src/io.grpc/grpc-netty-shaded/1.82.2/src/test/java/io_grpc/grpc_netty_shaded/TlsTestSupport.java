/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.security.Provider;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator;
import io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.ssl.ApplicationProtocolConfig;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslHandler;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.netty.shaded.io.netty.util.ReferenceCountUtil;

final class TlsTestSupport {
    private TlsTestSupport() {
    }

    static SslContext context(boolean server, Provider provider, SslProvider sslProvider, String... protocols)
            throws Exception {
        try (InputStream cert = resource("server-cert.pem"); InputStream key = resource("server-key.pem")) {
            SslContextBuilder builder = server ? SslContextBuilder.forServer(cert, key)
                    : SslContextBuilder.forClient().trustManager(cert);
            builder.sslProvider(sslProvider);
            if (provider != null) {
                builder.sslContextProvider(provider);
            }
            return builder.applicationProtocolConfig(new ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT, protocols)).build();
        }
    }

    static void exchange(boolean bouncyCastle, SslProvider sslProvider, String protocol, String message)
            throws Exception {
        Provider provider = bouncyCastle ? new BouncyCastleJsseProvider(new BouncyCastleProvider()) : null;
        SslContext serverContext = context(true, provider, sslProvider, protocol, "http/1.1");
        SslContext clientContext = context(false, provider, sslProvider, protocol);
        SslHandler serverHandler = serverContext.newHandler(ByteBufAllocator.DEFAULT);
        SslHandler clientHandler = clientContext.newHandler(ByteBufAllocator.DEFAULT, "localhost", 443);
        serverHandler.setHandshakeTimeout(30, TimeUnit.SECONDS);
        clientHandler.setHandshakeTimeout(30, TimeUnit.SECONDS);
        EmbeddedChannel server = new EmbeddedChannel(serverHandler);
        EmbeddedChannel client = new EmbeddedChannel(clientHandler);
        try {
            if (sslProvider == SslProvider.JDK) {
                assertNotNull(serverHandler.engine().getHandshakeApplicationProtocolSelector());
                assertEquals(null, clientHandler.engine().getHandshakeApplicationProtocol());
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!clientHandler.handshakeFuture().isDone() || !serverHandler.handshakeFuture().isDone()) {
                assertTrue(System.nanoTime() < deadline, "TLS handshake timed out");
                transfer(client, server);
                transfer(server, client);
            }
            assertTrue(clientHandler.handshakeFuture().isSuccess(),
                    () -> String.valueOf(clientHandler.handshakeFuture().cause()));
            assertTrue(serverHandler.handshakeFuture().isSuccess(),
                    () -> String.valueOf(serverHandler.handshakeFuture().cause()));
            assertEquals(protocol, clientHandler.applicationProtocol());
            assertEquals(protocol, serverHandler.applicationProtocol());
            client.writeOutbound(Unpooled.copiedBuffer(message, UTF_8));
            transfer(client, server);
            ByteBuf decoded = server.readInbound();
            assertNotNull(decoded);
            try {
                assertEquals(message, decoded.toString(UTF_8));
            } finally {
                decoded.release();
            }
        } finally {
            client.finishAndReleaseAll();
            server.finishAndReleaseAll();
            ReferenceCountUtil.release(clientContext);
            ReferenceCountUtil.release(serverContext);
        }
    }

    static void grpcExchange(String message, CallOptions options) throws Exception {
        SslContext serverContext;
        SslContext clientContext;
        try (InputStream cert = resource("server-cert.pem"); InputStream key = resource("server-key.pem")) {
            serverContext = GrpcSslContexts.configure(SslContextBuilder.forServer(cert, key), SslProvider.JDK).build();
        }
        try (InputStream cert = resource("server-cert.pem")) {
            clientContext = GrpcSslContexts.configure(SslContextBuilder.forClient().trustManager(cert),
                    SslProvider.JDK).build();
        }
        Server server = null;
        ManagedChannel channel = null;
        try {
            server = NettyServerBuilder.forPort(0).sslContext(serverContext).directExecutor()
                    .addService(Grpc_netty_shadedTest.echoService()).build().start();
            channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort()).overrideAuthority("localhost")
                    .sslContext(clientContext).directExecutor().build();
            assertEquals("echo:" + message, Grpc_netty_shadedTest.invokeUnary(channel, message, options));
        } finally {
            Grpc_netty_shadedTest.shutdown(channel, server);
        }
    }

    private static void transfer(EmbeddedChannel source, EmbeddedChannel destination) {
        source.runPendingTasks();
        Object packet;
        while ((packet = source.readOutbound()) != null) {
            destination.writeInbound(packet);
        }
        destination.runPendingTasks();
        source.checkException();
        destination.checkException();
    }

    private static InputStream resource(String name) {
        InputStream stream = TlsTestSupport.class.getResourceAsStream("/tls/" + name);
        assertNotNull(stream);
        return stream;
    }
}
