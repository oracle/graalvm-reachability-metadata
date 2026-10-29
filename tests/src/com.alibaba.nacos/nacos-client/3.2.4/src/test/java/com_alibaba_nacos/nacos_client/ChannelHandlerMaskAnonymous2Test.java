/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.ChannelHandlerContext;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.ChannelInboundHandlerAdapter;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class ChannelHandlerMaskAnonymous2Test {
    @Test
    void dispatchesInboundMessagesToOverriddenHandlerMethods() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        NioSocketChannel channel = new NioSocketChannel();
        CompletableFuture<String> received = new CompletableFuture<>();
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext context, Object message) {
                received.complete(((String) message).toUpperCase(Locale.ROOT));
            }
        });
        try {
            group.register(channel).get(30, TimeUnit.SECONDS);
            channel.pipeline().fireChannelRead("config");
            assertEquals("CONFIG", received.get(30, TimeUnit.SECONDS));
        } finally {
            assertTrue(channel.close().await(30, TimeUnit.SECONDS));
            assertTrue(group.shutdownGracefully(0, 30, TimeUnit.SECONDS).await(30, TimeUnit.SECONDS));
        }
    }
}
