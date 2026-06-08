/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.apache.hadoop.io.retry.AtMostOnce;
import org.apache.hadoop.io.retry.RetryPolicy;
import org.apache.hadoop.io.retry.RetryProxy;
import org.junit.jupiter.api.Test;

public class RetryInvocationHandlerTest {
    @Test
    void failedAtMostOnceInvocationIsReportedToRetryPolicy() {
        CapturingRetryPolicy retryPolicy = new CapturingRetryPolicy();
        FailingService service = (FailingService) RetryProxy.create(
                FailingService.class,
                new FailingServiceImplementation(),
                retryPolicy);

        assertThatThrownBy(service::readOnce)
                .isInstanceOf(IOException.class)
                .hasMessage("backend unavailable");

        assertThat(retryPolicy.observedException).isInstanceOf(IOException.class);
        assertThat(retryPolicy.observedRetries).isZero();
        assertThat(retryPolicy.observedFailovers).isZero();
        assertThat(retryPolicy.observedAtMostOnceOrIdempotent).isTrue();
    }

    public interface FailingService {
        @AtMostOnce
        String readOnce() throws IOException;
    }

    public static class FailingServiceImplementation implements FailingService {
        @Override
        public String readOnce() throws IOException {
            throw new IOException("backend unavailable");
        }
    }

    public static class CapturingRetryPolicy implements RetryPolicy {
        private Exception observedException;
        private int observedRetries = -1;
        private int observedFailovers = -1;
        private boolean observedAtMostOnceOrIdempotent;

        @Override
        public RetryAction shouldRetry(
                Exception exception,
                int retries,
                int failovers,
                boolean isIdempotentOrAtMostOnce) {
            observedException = exception;
            observedRetries = retries;
            observedFailovers = failovers;
            observedAtMostOnceOrIdempotent = isIdempotentOrAtMostOnce;
            return RetryAction.FAIL;
        }
    }
}
