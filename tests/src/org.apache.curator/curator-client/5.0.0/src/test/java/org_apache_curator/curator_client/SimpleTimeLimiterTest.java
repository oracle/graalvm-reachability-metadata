/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.curator.shaded.com.google.common.util.concurrent.SimpleTimeLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleTimeLimiterTest {
    public interface Service {
        String value();
    }

    @Test
    void invokesAnInterruptibleServiceThroughAProxy() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SimpleTimeLimiter limiter = SimpleTimeLimiter.create(executor);
            Service service = limiter.newProxy(
                    () -> "curator", Service.class, 10_000, java.util.concurrent.TimeUnit.MILLISECONDS);

            assertThat(service.value()).isEqualTo("curator");
        } finally {
            executor.shutdownNow();
        }
    }
}
