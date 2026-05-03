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

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ChannelManagerTest {
    @Test
    void nativeTransportSelectionInstantiatesConfiguredTransportFactory() throws Exception {
        AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder()
                .setUseNativeTransport(true)
                .setKeepAlive(false)
                .setIoThreadsCount(1)
                .setThreadFactory(new DefaultThreadFactory("ahc-native-transport-test", true))
                .setShutdownQuietPeriod(0)
                .setShutdownTimeout(1_000)
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
