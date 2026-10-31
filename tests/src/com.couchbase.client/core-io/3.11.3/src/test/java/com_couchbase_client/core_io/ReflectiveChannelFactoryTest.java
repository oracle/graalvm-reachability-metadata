/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.channel.ReflectiveChannelFactory;
import com.couchbase.client.core.deps.io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectiveChannelFactoryTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ReflectiveChannelFactory<NioSocketChannel> factory = new ReflectiveChannelFactory<>(NioSocketChannel.class);
        NioSocketChannel channel = factory.newChannel();
        try {
            assertTrue(channel.isOpen());
            assertFalse(channel.isActive());
            assertFalse(channel.isRegistered());
        } finally {
            channel.unsafe().closeForcibly();
        }
        assertFalse(channel.isOpen());
    }
}
