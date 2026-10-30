/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.channel.ChannelFuture;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerDomainSocketChannel;

public class NioDomainSocketUtilTest {
    @Test
    void removesTheSocketFileOnServerClose() throws Exception {
        Path directory = Files.createTempDirectory("netty-domain-");
        Path socket = directory.resolve("server.sock");
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        NioServerDomainSocketChannel channel = new NioServerDomainSocketChannel();
        try {
            ChannelFuture registration = group.register(channel);
            assertTrue(registration.await(30, TimeUnit.SECONDS));
            assertTrue(registration.isSuccess(), () -> String.valueOf(registration.cause()));
            ChannelFuture bind = channel.bind(UnixDomainSocketAddress.of(socket));
            assertTrue(bind.await(30, TimeUnit.SECONDS));
            assertTrue(bind.isSuccess(), () -> String.valueOf(bind.cause()));
            assertTrue(Files.exists(socket));
            assertTrue(channel.close().await(30, TimeUnit.SECONDS));
            assertFalse(Files.exists(socket));
        } finally {
            assertTrue(channel.close().await(30, TimeUnit.SECONDS));
            assertTrue(group.shutdownGracefully(0, 10, TimeUnit.SECONDS).await(30, TimeUnit.SECONDS));
            Files.deleteIfExists(socket);
            Files.delete(directory);
        }
    }
}
