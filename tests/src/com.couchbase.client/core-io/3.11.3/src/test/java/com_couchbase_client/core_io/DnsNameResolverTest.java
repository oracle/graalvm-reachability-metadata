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

public class DnsNameResolverTest {
    @Test
    void resolvesLocalhostForDnsTarget() throws Exception {
        try (GrpcTestSupport service = new GrpcTestSupport(true)) {
            assertEquals("reply:resolved", service.reply("resolved").get(30, TimeUnit.SECONDS));
        }
    }
}
