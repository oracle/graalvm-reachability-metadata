/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.pulsar.shade.com.google.common.util.concurrent.SimpleTimeLimiter;
import org.junit.jupiter.api.Test;

public class SimpleTimeLimiterTest {
    @Test
    void proxyInvokesTargetWithinBoundedTimeout() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SimpleTimeLimiter limiter = SimpleTimeLimiter.create(executor);
            Calculator proxy = limiter.newProxy(value -> value * 2, Calculator.class, 10, SECONDS);

            assertThat(proxy.doubleValue(21)).isEqualTo(42);
        } finally {
            executor.shutdownNow();
        }
    }

    public interface Calculator {
        int doubleValue(int value);
    }
}
