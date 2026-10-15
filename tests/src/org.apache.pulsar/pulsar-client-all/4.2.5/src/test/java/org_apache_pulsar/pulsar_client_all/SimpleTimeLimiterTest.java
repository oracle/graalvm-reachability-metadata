/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.shade.com.google.common.util.concurrent.SimpleTimeLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class SimpleTimeLimiterTest {
    @Test
    void invokesInterruptibleInterfaceThroughTimedProxy() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Greeting target = new GreetingService();
            Greeting proxy = SimpleTimeLimiter.create(executor)
                    .newProxy(target, Greeting.class, 10, TimeUnit.SECONDS);

            assertThat(proxy.greet("Pulsar")).isEqualTo("hello Pulsar");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    public interface Greeting {
        String greet(String name) throws InterruptedException;
    }

    public static final class GreetingService implements Greeting {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }
    }
}
