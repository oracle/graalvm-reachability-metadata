/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.channel.nio.NioEventLoopGroup;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NioIoHandlerAnonymous3Test {
    @Test
    void exercisesPublicApi() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            assertEquals(42, group.next().submit(() -> 21 * 2).get(10, TimeUnit.SECONDS));
        } finally {
            assertTrue(group.shutdownGracefully(0, 10, TimeUnit.SECONDS).await(10, TimeUnit.SECONDS));
        }
    }
}
