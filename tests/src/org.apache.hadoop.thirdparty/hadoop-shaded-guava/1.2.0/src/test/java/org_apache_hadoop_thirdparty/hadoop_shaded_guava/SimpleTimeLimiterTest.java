/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.thirdparty.com.google.common.util.concurrent.SimpleTimeLimiter;
import org.junit.jupiter.api.Test;

public class SimpleTimeLimiterTest {
    @Test
    void newProxyExecutesInterfaceMethodThroughTimeLimiter() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TimedService target = new TimedServiceImpl();
        try {
            SimpleTimeLimiter limiter = SimpleTimeLimiter.create(executor);
            TimedService proxy = limiter.newProxy(target, TimedService.class, 5, TimeUnit.SECONDS);

            assertEquals("hello Ada", proxy.greet("Ada"));
            assertEquals("interruptible result", proxy.interruptibleOperation());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    public interface TimedService {
        String greet(String name);

        String interruptibleOperation() throws InterruptedException;
    }

    private static final class TimedServiceImpl implements TimedService {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }

        @Override
        public String interruptibleOperation() throws InterruptedException {
            return "interruptible result";
        }
    }
}
