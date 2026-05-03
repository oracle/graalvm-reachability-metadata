/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.exporter.internal.grpc.GrpcStatusUtil;
import io.opentelemetry.exporter.internal.retry.RetryPolicy;
import io.opentelemetry.exporter.internal.retry.RetryUtil;
import org.junit.jupiter.api.Test;

public class RetryUtilTest {
    @Test
    void exposesRetryableStatusCodes() {
        assertThat(RetryUtil.retryableHttpResponseCodes())
                .containsExactlyInAnyOrder(429, 502, 503, 504);
        assertThat(RetryUtil.retryableGrpcStatusCodes())
                .containsExactlyInAnyOrder(
                        GrpcStatusUtil.GRPC_STATUS_CANCELLED,
                        GrpcStatusUtil.GRPC_STATUS_DEADLINE_EXCEEDED,
                        GrpcStatusUtil.GRPC_STATUS_RESOURCE_EXHAUSTED,
                        GrpcStatusUtil.GRPC_STATUS_ABORTED,
                        GrpcStatusUtil.GRPC_STATUS_OUT_OF_RANGE,
                        GrpcStatusUtil.GRPC_STATUS_UNAVAILABLE,
                        GrpcStatusUtil.GRPC_STATUS_DATA_LOSS);
    }

    @Test
    void inspectsDelegateFieldWhenSettingRetryPolicy() {
        DelegateHolder holder = new DelegateHolder(null);

        assertThatThrownBy(
                        () -> RetryUtil.setRetryPolicyOnDelegate(holder, RetryPolicy.getDefault()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delegate field");
    }

    static final class DelegateHolder {
        private final Object delegate;

        private DelegateHolder(Object delegate) {
            this.delegate = delegate;
        }
    }
}
