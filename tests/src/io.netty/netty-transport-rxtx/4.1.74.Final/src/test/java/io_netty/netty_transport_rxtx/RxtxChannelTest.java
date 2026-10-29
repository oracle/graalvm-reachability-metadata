/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_rxtx;

import java.util.concurrent.TimeUnit;

import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.rxtx.RxtxChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(60)
public class RxtxChannelTest {
    @Test
    void registersAndClosesAnUnconnectedChannel() throws Exception {
        OioEventLoopGroup group = new OioEventLoopGroup(1);
        RxtxChannel channel = new RxtxChannel();
        try {
            assertTrue(channel.isOpen());
            assertFalse(channel.isActive());
            assertFalse(channel.isRegistered());
            assertNull(channel.remoteAddress());
            assertEquals("localhost", channel.localAddress().value());
            assertSame(channel.config(), channel.config());

            group.register(channel).get(10, TimeUnit.SECONDS);
            assertTrue(channel.isRegistered());
            assertSame(group, channel.eventLoop().parent());

            channel.close().get(10, TimeUnit.SECONDS);
            channel.closeFuture().get(10, TimeUnit.SECONDS);
            assertFalse(channel.isOpen());
            assertFalse(channel.isActive());
            assertTrue(channel.closeFuture().isSuccess());

            channel.close().get(10, TimeUnit.SECONDS);
            assertFalse(channel.isOpen());
        } finally {
            try {
                if (channel.isRegistered()) {
                    channel.close().get(10, TimeUnit.SECONDS);
                }
            } finally {
                group.shutdownGracefully(0, 10, TimeUnit.SECONDS).get(15, TimeUnit.SECONDS);
            }
        }
    }
}
