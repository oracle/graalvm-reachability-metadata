/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.socket.InternetProtocolFamily;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import org.junit.jupiter.api.Test;

public class SelectorProviderUtilTest {
    @Test
    void createsAnIpv4SocketChannelThroughTheSelectorProvider() {
        NioSocketChannel channel = new NioSocketChannel(SelectorProvider.provider(), InternetProtocolFamily.IPv4);
        try {
            assertTrue(channel.isOpen());
        } finally {
            channel.unsafe().closeForcibly();
        }
    }
}
