/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.grpc.internal.TimeProvider;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class InstantTimeProviderTest {
    @Test
    void exercisesPublicApi() throws Exception {
        long before = TimeUnit.SECONDS.toNanos(Instant.now().getEpochSecond());
        long now = TimeProvider.SYSTEM_TIME_PROVIDER.currentTimeNanos();
        long after = TimeUnit.SECONDS.toNanos(Instant.now().getEpochSecond() + 1);
        assertTrue(now >= before);
        assertTrue(now < after);
    }
}
