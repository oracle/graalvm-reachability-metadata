/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.util.concurrent.TimeUnit;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NioEventLoopTest {

    @Test
    public void executesTasksOnNioEventLoop() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Future<String> task = group.next().submit(() -> "executed");

            Assertions.assertTrue(task.await(10, TimeUnit.SECONDS), "Timed out waiting for NIO event loop task");
            Assertions.assertTrue(task.isSuccess(), () -> "NIO event loop task failed: " + task.cause());
            Assertions.assertEquals("executed", task.getNow());
        } finally {
            Assertions.assertTrue(group.shutdownGracefully().await(10, TimeUnit.SECONDS),
                    "Timed out shutting down NIO event loop group");
        }
    }
}
