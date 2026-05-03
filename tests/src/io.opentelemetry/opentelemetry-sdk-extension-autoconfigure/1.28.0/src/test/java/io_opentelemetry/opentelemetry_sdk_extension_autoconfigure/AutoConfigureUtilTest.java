/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk_extension_autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AutoConfigureUtilTest {
    @Test
    void readsAutoConfiguredSdkConfigThroughUtility() {
        AutoConfiguredOpenTelemetrySdk configured = null;

        try {
            configured = AutoConfiguredOpenTelemetrySdk.builder()
                    .disableShutdownHook()
                    .addPropertiesCustomizer(config -> configuredProperties())
                    .build();

            ConfigProperties config = AutoConfigureUtil.getConfig(configured);

            assertThat(config.getString("test.config.key")).isEqualTo("configured-value");
            assertThat(config.getList("test.config.list")).containsExactly("first", "second");
            assertThat(config.getString("otel.service.name")).isEqualTo("auto-configure-util-test");
        } finally {
            if (configured != null) {
                configured.getOpenTelemetrySdk().close();
            }
        }
    }

    private static Map<String, String> configuredProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otel.sdk.disabled", "false");
        properties.put("otel.traces.exporter", "none");
        properties.put("otel.metrics.exporter", "none");
        properties.put("otel.logs.exporter", "none");
        properties.put("otel.propagators", "none");
        properties.put("otel.traces.sampler", "always_off");
        properties.put("otel.service.name", "auto-configure-util-test");
        properties.put("test.config.key", "configured-value");
        properties.put("test.config.list", "first,second");
        return properties;
    }
}
