/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import org.junit.jupiter.api.Test;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.TlsChannelCredentials;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.SelfSignedCertificate;

public class X509AuthorityVerifierTest {
    @Test
    void channelCredentialsValidateTheOverriddenRpcHostname() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost", "RSA", 2048);
        Server server = null;
        ManagedChannel channel = null;
        try {
            KeyStore roots = KeyStore.getInstance(KeyStore.getDefaultType());
            roots.load(null, null);
            roots.setCertificateEntry("server", certificate.cert());
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(roots);
            RecordingTrustManager trustManager = recordingTrustManager(factory);
            SslContext serverTls = GrpcSslContexts.configure(
                    SslContextBuilder.forServer(certificate.key(), certificate.cert()), SslProvider.JDK).build();
            server = NettyServerBuilder.forPort(0).sslContext(serverTls).directExecutor()
                    .addService(Grpc_netty_shadedTest.echoService()).build().start();
            channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort(),
                    TlsChannelCredentials.newBuilder().trustManager(trustManager).build())
                    .overrideAuthority("localhost").directExecutor().build();
            assertEquals("echo:credential authority", Grpc_netty_shadedTest.invokeUnary(
                    channel, "credential authority", CallOptions.DEFAULT.withAuthority("LOCALHOST:443")));
            assertTrue(trustManager.peerHosts.contains("localhost"));
            assertTrue(trustManager.peerHosts.contains("LOCALHOST"));
        } finally {
            Grpc_netty_shadedTest.shutdown(channel, server);
            certificate.delete();
        }
    }

    private static RecordingTrustManager recordingTrustManager(TrustManagerFactory factory) {
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509ExtendedTrustManager extended) {
                return new RecordingTrustManager(extended);
            }
        }
        throw new IllegalStateException("The TLS provider did not supply an extended trust manager");
    }

    @Test
    void verifiesPerRpcAuthorityAgainstTheTlsPeerCertificate() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost", "RSA", 2048);
        Server server = null;
        ManagedChannel channel = null;
        try {
            SslContext serverTls = GrpcSslContexts.configure(
                    SslContextBuilder.forServer(certificate.key(), certificate.cert()), SslProvider.JDK).build();
            SslContext clientTls = GrpcSslContexts.configure(
                    SslContextBuilder.forClient().trustManager(certificate.cert()), SslProvider.JDK).build();
            server = NettyServerBuilder.forPort(0).sslContext(serverTls).directExecutor()
                    .addService(Grpc_netty_shadedTest.echoService()).build().start();
            channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .overrideAuthority("localhost").sslContext(clientTls).directExecutor().build();
            assertEquals("echo:verified authority", Grpc_netty_shadedTest.invokeUnary(
                    channel, "verified authority", CallOptions.DEFAULT.withAuthority("localhost:443")));
        } finally {
            Grpc_netty_shadedTest.shutdown(channel, server);
            certificate.delete();
        }
    }

    private static final class RecordingTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager delegate;
        private final Queue<String> peerHosts = new ConcurrentLinkedQueue<>();

        private RecordingTrustManager(X509ExtendedTrustManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            delegate.checkServerTrusted(chain, authType, engine);
            peerHosts.add(engine.getPeerHost());
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            delegate.checkClientTrusted(chain, authType, engine);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            delegate.checkServerTrusted(chain, authType, socket);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            delegate.checkClientTrusted(chain, authType, socket);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }
}
