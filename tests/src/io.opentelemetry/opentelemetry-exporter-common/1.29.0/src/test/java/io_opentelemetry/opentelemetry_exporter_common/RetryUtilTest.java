/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_common;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.internal.RetryUtil;
import io.opentelemetry.exporter.internal.grpc.GrpcExporterUtil;
import org.junit.jupiter.api.Test;

public class RetryUtilTest {
    @Test
    void exposesRetryableStatusCodes() {
        assertThat(RetryUtil.retryableHttpResponseCodes())
                .containsExactlyInAnyOrder(429, 502, 503, 504);
        assertThat(RetryUtil.retryableGrpcStatusCodes())
                .containsExactlyInAnyOrder(
                        String.valueOf(GrpcExporterUtil.GRPC_STATUS_CANCELLED),
                        String.valueOf(GrpcExporterUtil.GRPC_STATUS_DEADLINE_EXCEEDED),
                        String.valueOf(GrpcExporterUtil.GRPC_STATUS_RESOURCE_EXHAUSTED),
                        String.valueOf(GrpcExporterUtil.GRPC_STATUS_ABORTED),
                        String.valueOf(GrpcExporterUtil.GRPC_STATUS_OUT_OF_RANGE),
                        String.valueOf(GrpcExporterUtil.GRPC_STATUS_UNAVAILABLE),
                        String.valueOf(GrpcExporterUtil.GRPC_STATUS_DATA_LOSS));
    }

    @Test
    void excludesNonRetryableStatusCodes() {
        assertThat(RetryUtil.retryableHttpResponseCodes())
                .doesNotContain(200, 400, 500);
        assertThat(RetryUtil.retryableGrpcStatusCodes())
                .doesNotContain("0", "3", "5");
    }
}
