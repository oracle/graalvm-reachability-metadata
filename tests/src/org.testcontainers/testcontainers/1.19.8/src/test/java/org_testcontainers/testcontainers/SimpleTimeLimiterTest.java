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

public class SimpleTimeLimiterTest {
    @Test
    void invokesInterruptibleMethodsThroughABoundedProxy() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Greeting proxy = SimpleTimeLimiter.create(executor)
                .newProxy(new GreetingService(), Greeting.class, 10, TimeUnit.SECONDS);

            assertThat(proxy.greet("Ada")).isEqualTo("Hello Ada");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    public interface Greeting {
        String greet(String name) throws InterruptedException;
    }

    public static class GreetingService implements Greeting {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
