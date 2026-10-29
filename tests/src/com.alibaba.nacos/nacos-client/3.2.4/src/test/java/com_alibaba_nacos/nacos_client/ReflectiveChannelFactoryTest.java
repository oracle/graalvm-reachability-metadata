/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.ReflectiveChannelFactory;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

public class ReflectiveChannelFactoryTest {
    @Test
    void createsUnconnectedSocketChannels() {
        ReflectiveChannelFactory<NioSocketChannel> factory = new ReflectiveChannelFactory<>(NioSocketChannel.class);
        NioSocketChannel channel = factory.newChannel();
        try {
            assertTrue(channel.isOpen());
            assertFalse(channel.isActive());
        } finally {
            channel.unsafe().closeForcibly();
        }
        assertFalse(channel.isOpen());
    }
}
