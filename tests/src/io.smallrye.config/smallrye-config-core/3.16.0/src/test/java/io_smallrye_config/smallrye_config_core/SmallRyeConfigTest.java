/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class SmallRyeConfigTest {
    @Test
    void resolvesIndexedPropertiesAsArrayValue() {
        SmallRyeConfig config = configWithProperties(Map.of(
                "services[0]", "auth",
                "services[1]", "billing",
                "services[2]", "notifications"));

        String[] services = config.getValue("services", String[].class);

        assertThat(services).containsExactly("auth", "billing", "notifications");
    }

    @Test
    void resolvesIndexedPropertiesAsOptionalArrayValue() {
        SmallRyeConfig config = configWithProperties(Map.of(
                "ports[0]", "8080",
                "ports[1]", "8443"));

        Optional<Integer[]> ports = config.getOptionalValue("ports", Integer[].class);

        assertThat(ports).hasValueSatisfying(values -> assertThat(values).containsExactly(8080, 8443));
    }

    private static SmallRyeConfig configWithProperties(final Map<String, String> properties) {
        return new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(properties, "smallrye-config-test"))
                .build();
    }
}
