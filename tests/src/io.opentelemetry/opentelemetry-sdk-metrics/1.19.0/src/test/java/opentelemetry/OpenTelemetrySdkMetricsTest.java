/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <
 *
 * http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarFilter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class OpenTelemetrySdkMetricsTest {

    @Test
    public void sdkMetricsTest() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        SdkMeterProviderBuilder sdkMeterProvider = SdkMeterProvider.builder();

        Method method =
                SdkMeterProviderBuilder.class.getDeclaredMethod(
                        "setExemplarFilter", ExemplarFilter.class);
        method.setAccessible(true);
        method.invoke(sdkMeterProvider, new ExemplarFilter() {
            @Override
            public boolean shouldSampleMeasurement(long value, Attributes attributes, Context context) {
                return false;
            }

            @Override
            public boolean shouldSampleMeasurement(double value, Attributes attributes, Context context) {
                return false;
            }
        });

        try (SdkMeterProvider ignored = sdkMeterProvider.build()) {
            ignored.meterBuilder("test");
        }

    }
}
