/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.channel.socket.InternetProtocolFamily;
import com.couchbase.client.core.deps.io.netty.channel.socket.nio.NioServerSocketChannel;
import com.couchbase.client.core.deps.io.netty.channel.socket.nio.NioSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SelectorProviderUtilTest {
    @Test
    void exercisesPublicApi() throws Exception {
        NioSocketChannel client = new NioSocketChannel(SelectorProvider.provider(), InternetProtocolFamily.IPv4);
        NioServerSocketChannel server = new NioServerSocketChannel(SelectorProvider.provider(), InternetProtocolFamily.IPv4);
        try {
            assertTrue(client.isOpen());
            assertTrue(server.isOpen());
            assertFalse(client.isActive());
        } finally {
            client.unsafe().closeForcibly();
            server.unsafe().closeForcibly();
        }
    }
}
