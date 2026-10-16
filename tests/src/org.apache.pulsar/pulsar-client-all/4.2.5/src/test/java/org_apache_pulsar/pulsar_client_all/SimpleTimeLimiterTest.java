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

public class SimpleTimeLimiterTest {
    @Test
    void invokesServiceMethodsThroughATimeLimitedProxy() {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final EchoService proxy = SimpleTimeLimiter.create(executor)
                    .newProxy(new EchoServiceImpl(), EchoService.class, 10, TimeUnit.SECONDS);

            assertThat(proxy.echo("pulsar")).isEqualTo("pulsar");
        } finally {
            executor.shutdownNow();
        }
    }

    public interface EchoService {
        String echo(String value);
    }

    public static final class EchoServiceImpl implements EchoService {
        @Override
        public String echo(String value) {
            return value;
        }
    }
}
