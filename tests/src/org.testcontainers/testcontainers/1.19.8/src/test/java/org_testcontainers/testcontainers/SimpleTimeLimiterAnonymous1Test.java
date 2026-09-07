/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.util.concurrent.SimpleTimeLimiter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleTimeLimiterAnonymous1Test {
    @Test
    void proxyDispatchesCallsToTheTargetObject() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Calculator proxy = SimpleTimeLimiter.create(executor)
                .newProxy(new CalculatorService(), Calculator.class, 10, TimeUnit.SECONDS);

            assertThat(proxy.doubleValue(6)).isEqualTo(12);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    public interface Calculator {
        int doubleValue(int value);
    }

    public static class CalculatorService implements Calculator {
        @Override
        public int doubleValue(int value) {
            return value * 2;
        }
    }
}
