/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package opentelemetry;

import io.opentelemetry.sdk.metrics.ExemplarFilter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import org.junit.jupiter.api.Test;

public class OpenTelemetrySdkMetricsTest {

    @Test
    public void sdkMetricsTest() {
        SdkMeterProviderBuilder sdkMeterProvider =
                SdkMeterProvider.builder().setExemplarFilter(ExemplarFilter.alwaysOff());

        try (SdkMeterProvider provider = sdkMeterProvider.build()) {
            provider.meterBuilder("test");
        }
    }
}
