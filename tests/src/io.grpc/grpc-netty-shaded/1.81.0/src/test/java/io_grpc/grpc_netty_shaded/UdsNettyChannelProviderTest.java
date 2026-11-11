/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress;

public class UdsNettyChannelProviderTest {
    @TempDir
    Path directory;

    @Test
    void discoversUnixDomainTransportAndCompletesAnRpc() throws Exception {
        Path socket = directory.resolve("grpc.sock");
        EpollEventLoopGroup group = new EpollEventLoopGroup(1);
        Server server = null;
        ManagedChannel channel = null;
        try {
            server = NettyServerBuilder.forAddress(new DomainSocketAddress(socket.toFile()))
                    .channelType(EpollServerDomainSocketChannel.class)
                    .bossEventLoopGroup(group).workerEventLoopGroup(group)
                    .directExecutor().addService(Grpc_netty_shadedTest.echoService())
                    .build().start();
            channel = Grpc.newChannelBuilder("unix:" + socket, InsecureChannelCredentials.create())
                    .directExecutor().build();
            assertEquals("echo:unix transport", Grpc_netty_shadedTest.invokeUnary(channel, "unix transport"));
        } finally {
            Grpc_netty_shadedTest.shutdown(channel, server);
            assertTrue(group.shutdownGracefully(0, 30, TimeUnit.SECONDS).await(30, TimeUnit.SECONDS));
        }
    }
}
