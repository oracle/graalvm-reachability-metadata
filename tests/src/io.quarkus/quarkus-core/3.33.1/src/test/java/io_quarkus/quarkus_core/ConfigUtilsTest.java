/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.runtime.configuration.ConfigUtils;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;

public class ConfigUtilsTest {
    @Test
    void resolvesQuarkusProfilesAndExpandsExpressions() {
        SmallRyeConfig config = ConfigUtils.configBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "quarkus.profile", "blue,green",
                        "app.host", "base.example",
                        "%blue.app.host", "blue.example",
                        "%green.app.host", "green.example",
                        "app.endpoint", "https://${app.host}/items"), "profile-settings", 500))
                .build();

        assertThat(config.getProfiles()).containsExactly("green", "blue");
        assertThat(config.getValue("app.host", String.class)).isEqualTo("green.example");
        assertThat(config.getValue("app.endpoint", String.class)).isEqualTo("https://green.example/items");
    }

    @Test
    void usesHigherPrioritySourceBeforeDefaults() {
        SmallRyeConfig config = ConfigUtils.emptyConfigBuilder()
                .withDefaultValue("app.workers", "2")
                .withSources(new PropertiesConfigSource(Map.of("app.workers", "8"), "worker-settings", 500))
                .build();

        assertThat(config.getValue("app.workers", Integer.class)).isEqualTo(8);
        assertThat(config.getOptionalValue("app.absent", String.class)).isEmpty();
    }
}
