/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.util.concurrent.TimeUnit;

import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NioEventLoopTest {
    @Test
    void eventLoopGroupCreatesNioEventLoopWithSelector() throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            EventLoop eventLoop = group.next();

            Assertions.assertTrue(eventLoop instanceof NioEventLoop);
            NioEventLoop nioEventLoop = (NioEventLoop) eventLoop;
            nioEventLoop.setIoRatio(100);
            Assertions.assertEquals(100, nioEventLoop.getIoRatio());
        } finally {
            group.shutdownGracefully(0L, 5L, TimeUnit.SECONDS).sync();
        }
    }
}
