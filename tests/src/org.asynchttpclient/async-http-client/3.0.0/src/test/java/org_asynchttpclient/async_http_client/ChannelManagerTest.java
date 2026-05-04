/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_asynchttpclient.async_http_client;

import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.netty.channel.ChannelManager;
import org.junit.jupiter.api.Test;

import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class ChannelManagerTest {
    @Test
    void externalUnknownEventLoopGroupChecksNativeTransportTypes() throws Exception {
        DefaultEventLoopGroup eventLoopGroup = new DefaultEventLoopGroup(
                1,
                new DefaultThreadFactory("ahc-external-event-loop-test", true));
        AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder()
                .setEventLoopGroup(eventLoopGroup)
                .setKeepAlive(false)
                .setThreadFactory(new DefaultThreadFactory("ahc-external-event-loop-config-test", true))
                .setShutdownQuietPeriod(Duration.ZERO)
                .setShutdownTimeout(Duration.ofSeconds(1))
                .build();
        Timer timer = new HashedWheelTimer(
                new DefaultThreadFactory("ahc-external-event-loop-test-timer", true),
                10,
                TimeUnit.MILLISECONDS,
                32);

        assertThat(EpollEventLoopGroup.class.getName()).contains("EpollEventLoopGroup");

        try {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new ChannelManager(config, timer))
                    .withMessageContaining("Unknown event loop group DefaultEventLoopGroup");
        } finally {
            eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).await(5, TimeUnit.SECONDS);
            timer.stop();
        }
    }

    @Test
    void nativeTransportSelectionInstantiatesConfiguredTransportFactory() throws Exception {
        AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder()
                .setUseNativeTransport(true)
                .setKeepAlive(false)
                .setIoThreadsCount(1)
                .setThreadFactory(new DefaultThreadFactory("ahc-native-transport-test", true))
                .setShutdownQuietPeriod(Duration.ZERO)
                .setShutdownTimeout(Duration.ofSeconds(1))
                .build();
        Timer timer = new HashedWheelTimer(
                new DefaultThreadFactory("ahc-native-transport-test-timer", true),
                10,
                TimeUnit.MILLISECONDS,
                32);
        ChannelManager channelManager = null;

        try {
            channelManager = new ChannelManager(config, timer);

            assertThat(channelManager.getEventLoopGroup().getClass().getName())
                    .containsAnyOf("epoll", "kqueue");
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessageContaining("No suitable native transport");
        } finally {
            if (channelManager != null) {
                channelManager.close();
                boolean terminated = channelManager.getEventLoopGroup()
                        .terminationFuture()
                        .await(5, TimeUnit.SECONDS);
                assertThat(terminated).isTrue();
            }
            timer.stop();
        }
    }
}
