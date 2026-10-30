/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.util.concurrent.SimpleTimeLimiter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleTimeLimiterAnonymous1Anonymous1Test {
    @Test
    void invokesTargetMethodOnExecutorThread() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            TimeLimitedService service = SimpleTimeLimiter.create(executor)
                    .newProxy(new TimeLimitedService.Implementation(), TimeLimitedService.class, 10, TimeUnit.SECONDS);
            assertEquals(9, service.length("executor!"));
        } finally {
            executor.shutdownNow();
        }
    }
}
