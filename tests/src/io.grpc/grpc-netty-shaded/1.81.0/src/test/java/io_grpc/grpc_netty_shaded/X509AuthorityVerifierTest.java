/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.SelfSignedCertificate;

public class X509AuthorityVerifierTest {
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
}
