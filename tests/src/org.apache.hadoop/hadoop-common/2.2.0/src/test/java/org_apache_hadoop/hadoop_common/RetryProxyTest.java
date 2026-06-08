/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.io.retry.RetryPolicy;
import org.apache.hadoop.io.retry.RetryProxy;
import org.junit.jupiter.api.Test;

public class RetryProxyTest {
    @Test
    void methodSpecificPolicyAllowsConfiguredMethodToRetry() throws IOException {
        RetryOncePolicy retryOncePolicy = new RetryOncePolicy();
        Map<String, RetryPolicy> policiesByMethodName = new HashMap<>();
        policiesByMethodName.put("unstable", retryOncePolicy);
        FlakyServiceImplementation implementation = new FlakyServiceImplementation();
        FlakyService service = (FlakyService) RetryProxy.create(
                FlakyService.class,
                implementation,
                policiesByMethodName);

        String result = service.unstable("hadoop");

        assertThat(result).isEqualTo("recovered:hadoop");
        assertThat(implementation.attempts).isEqualTo(2);
        assertThat(retryOncePolicy.observedRetries).isZero();
        assertThat(retryOncePolicy.invocations).isEqualTo(1);
    }

    public interface FlakyService {
        String unstable(String value) throws IOException;
    }

    public static class FlakyServiceImplementation implements FlakyService {
        private int attempts;

        @Override
        public String unstable(String value) throws IOException {
            attempts++;
            if (attempts == 1) {
                throw new IOException("first attempt failed");
            }
            return "recovered:" + value;
        }
    }

    public static class RetryOncePolicy implements RetryPolicy {
        private int observedRetries = -1;
        private int invocations;

        @Override
        public RetryAction shouldRetry(
                Exception exception,
                int retries,
                int failovers,
                boolean isIdempotentOrAtMostOnce) {
            observedRetries = retries;
            invocations++;
            return RetryAction.RETRY;
        }
    }
}
