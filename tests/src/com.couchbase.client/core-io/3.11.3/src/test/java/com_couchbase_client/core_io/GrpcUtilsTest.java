/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GrpcUtilsTest {
    @Test
    void exchangesUnaryMessagesUsingDefaultNettyTransport() throws Exception {
        try (GrpcTestSupport service = new GrpcTestSupport(false)) {
            assertEquals("reply:hello", service.reply("hello").get(30, TimeUnit.SECONDS));
            assertEquals("reply:another request", service.reply("another request").get(30, TimeUnit.SECONDS));
        }
    }
}
