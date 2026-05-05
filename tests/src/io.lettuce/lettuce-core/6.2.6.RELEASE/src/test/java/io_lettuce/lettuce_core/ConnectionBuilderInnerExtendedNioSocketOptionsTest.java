/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ConnectionBuilder;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.resource.DefaultClientResources;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jdk.net.ExtendedSocketOptions;
import org.junit.jupiter.api.Test;

public class ConnectionBuilderInnerExtendedNioSocketOptionsTest {

    static {
        System.setProperty("io.lettuce.core.epoll", "false");
        System.setProperty("io.lettuce.core.iouring", "false");
        System.setProperty("io.lettuce.core.kqueue", "false");
    }

    @Test
    void extendedKeepAliveOptionsAreAppliedToNioBootstrap() throws Exception {
        SocketOptions.KeepAliveOptions keepAlive = SocketOptions.KeepAliveOptions.builder()
                .enable()
                .count(4)
                .idle(Duration.ofSeconds(30))
                .interval(Duration.ofSeconds(15))
                .build();
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(SocketOptions.builder().keepAlive(keepAlive).build())
                .build();
        Bootstrap bootstrap = new Bootstrap();
        DefaultClientResources clientResources = DefaultClientResources.create();
        AtomicReference<EventLoopGroup> eventLoopGroup = new AtomicReference<>();

        try {
            ConnectionBuilder.connectionBuilder()
                    .bootstrap(bootstrap)
                    .clientOptions(clientOptions)
                    .clientResources(clientResources)
                    .configureBootstrap(false, eventLoopGroupClass -> {
                        assertThat(eventLoopGroupClass).isEqualTo(NioEventLoopGroup.class);
                        NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup(1);
                        eventLoopGroup.set(nioEventLoopGroup);
                        return nioEventLoopGroup;
                    });

            Map<ChannelOption<?>, Object> options = bootstrap.config().options();
            assertBootstrapOption(options, ExtendedSocketOptions.TCP_KEEPCOUNT.name(), 4);
            assertBootstrapOption(options, ExtendedSocketOptions.TCP_KEEPIDLE.name(), 30);
            assertBootstrapOption(options, ExtendedSocketOptions.TCP_KEEPINTERVAL.name(), 15);
        } finally {
            EventLoopGroup group = eventLoopGroup.get();
            if (group != null) {
                group.shutdownGracefully(0, 2, TimeUnit.SECONDS).await(2, TimeUnit.SECONDS);
            }
            clientResources.shutdown(0, 2, TimeUnit.SECONDS).await(2, TimeUnit.SECONDS);
        }
    }

    private static void assertBootstrapOption(Map<ChannelOption<?>, Object> options, String name, int value) {
        assertThat(options.entrySet())
                .anySatisfy(entry -> {
                    assertThat(entry.getKey().toString()).isEqualTo(name);
                    assertThat(entry.getValue()).isEqualTo(value);
                });
    }
}
