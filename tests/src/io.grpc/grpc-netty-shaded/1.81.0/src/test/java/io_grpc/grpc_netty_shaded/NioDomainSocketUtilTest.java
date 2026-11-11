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
import org.junit.jupiter.api.io.TempDir;

import io.grpc.netty.shaded.io.netty.bootstrap.ServerBootstrap;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.netty.shaded.io.netty.channel.ChannelFuture;
import io.grpc.netty.shaded.io.netty.channel.ChannelInboundHandlerAdapter;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerDomainSocketChannel;

public class NioDomainSocketUtilTest {
    @TempDir
    Path directory;

    @Test
    void closingDomainSocketServerRemovesItsSocketFile() throws Exception {
        Path socket = directory.resolve("listener.sock");
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        Channel server = null;
        try {
            ChannelFuture bound = new ServerBootstrap().group(group)
                    .channel(NioServerDomainSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter())
                    .bind(UnixDomainSocketAddress.of(socket));
            assertTrue(bound.await(30, TimeUnit.SECONDS));
            bound.sync();
            server = bound.channel();
            assertTrue(Files.exists(socket));
            ChannelFuture closed = server.close();
            assertTrue(closed.await(30, TimeUnit.SECONDS));
            assertTrue(closed.isSuccess(), () -> String.valueOf(closed.cause()));
            assertFalse(Files.exists(socket));
        } finally {
            if (server != null) {
                assertTrue(server.close().await(30, TimeUnit.SECONDS));
            }
            assertTrue(group.shutdownGracefully(0, 30, TimeUnit.SECONDS).await(30, TimeUnit.SECONDS));
        }
    }
}
