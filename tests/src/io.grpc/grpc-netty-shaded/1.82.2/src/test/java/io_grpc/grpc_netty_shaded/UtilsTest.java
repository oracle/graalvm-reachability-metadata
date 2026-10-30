/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

public class UtilsTest {
    @Test
    void supportsKeepAliveOnTheDefaultTransport() throws Exception {
        Server server = null;
        ManagedChannel channel = null;
        try {
            server = NettyServerBuilder.forPort(0).directExecutor()
                    .addService(Grpc_netty_shadedTest.echoService()).build().start();
            channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .keepAliveTime(30, TimeUnit.SECONDS).keepAliveTimeout(10, TimeUnit.SECONDS)
                    .usePlaintext().directExecutor().build();
            assertEquals("echo:keepalive", Grpc_netty_shadedTest.invokeUnary(channel, "keepalive"));
        } finally {
            Grpc_netty_shadedTest.shutdown(channel, server);
        }
    }
}
