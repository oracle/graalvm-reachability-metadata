/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.bootstrap.ServerBootstrap;
import com.couchbase.client.core.deps.io.netty.channel.Channel;
import com.couchbase.client.core.deps.io.netty.channel.ChannelFuture;
import com.couchbase.client.core.deps.io.netty.channel.ChannelInboundHandlerAdapter;
import com.couchbase.client.core.deps.io.netty.channel.nio.NioEventLoopGroup;
import com.couchbase.client.core.deps.io.netty.channel.socket.nio.NioServerDomainSocketChannel;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NioDomainSocketUtilTest {
    @Test
    void removesBoundSocketFileOnClose(@TempDir Path directory) throws Exception {
        Path socket = directory.resolve("server.sock");
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        Channel server = null;
        try {
            ChannelFuture binding = new ServerBootstrap().group(group)
                    .channel(NioServerDomainSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter())
                    .bind(UnixDomainSocketAddress.of(socket));
            server = binding.channel();
            assertTrue(binding.await(10, TimeUnit.SECONDS));
            binding.sync();
            assertEquals(UnixDomainSocketAddress.of(socket), server.localAddress());
            assertTrue(Files.exists(socket));
            assertTrue(server.close().await(10, TimeUnit.SECONDS));
            assertFalse(Files.exists(socket));
        } finally {
            if (server != null) {
                assertTrue(server.close().await(10, TimeUnit.SECONDS));
            }
            assertTrue(group.shutdownGracefully(0, 10, TimeUnit.SECONDS).await(10, TimeUnit.SECONDS));
        }
    }
}
