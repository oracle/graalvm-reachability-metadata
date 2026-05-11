/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class NioEventLoopTest {
    @Test
    void createsEventLoopWithOptimizedSelectorAndRunsTask() throws Exception {
        System.setProperty("io.netty.noKeySetOptimization", "false");

        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            EventLoop eventLoop = group.next();
            assertThat(eventLoop).isInstanceOf(NioEventLoop.class);

            NioEventLoop nioEventLoop = (NioEventLoop) eventLoop;
            assertThat(nioEventLoop.getIoRatio()).isEqualTo(50);

            CountDownLatch taskCompleted = new CountDownLatch(1);
            eventLoop.execute(new Runnable() {
                @Override
                public void run() {
                    taskCompleted.countDown();
                }
            });

            assertThat(taskCompleted.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
            assertThat(group.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
