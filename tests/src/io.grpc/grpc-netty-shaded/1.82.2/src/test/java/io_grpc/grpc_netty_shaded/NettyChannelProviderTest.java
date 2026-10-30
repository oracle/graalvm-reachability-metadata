/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

public class NettyChannelProviderTest {
    @Test
    void discoversTheTransportProviderAndRunsAnRpc() throws Exception {
        Server server = null;
        ManagedChannel channel = null;
        try {
            server = NettyServerBuilder.forPort(0).directExecutor()
                    .addService(Grpc_netty_shadedTest.echoService()).build().start();
            channel = Grpc.newChannelBuilderForAddress("127.0.0.1", server.getPort(),
                    InsecureChannelCredentials.create()).directExecutor().build();
            assertEquals("echo:provider", Grpc_netty_shadedTest.invokeUnary(channel, "provider"));
        } finally {
            Grpc_netty_shadedTest.shutdown(channel, server);
        }
    }
}
