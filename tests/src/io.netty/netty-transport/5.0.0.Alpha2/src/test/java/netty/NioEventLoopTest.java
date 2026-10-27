/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NioEventLoopTest {
    @Test
    void runsTaskOnNioEventLoop() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        CountDownLatch taskCompleted = new CountDownLatch(1);
        try {
            group.execute(taskCompleted::countDown);

            assertThat(taskCompleted.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            group.shutdownGracefully().sync();
        }
    }
}
