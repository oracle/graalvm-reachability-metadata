/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.config.ClasspathOverrideSource;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigContent.OverrideContent;

import org.junit.jupiter.api.Test;

public class ClasspathOverrideSourceInnerBuilderTest {
    private static final String OVERRIDES_RESOURCE = "io_helidon_config/helidon_config/classpath-overrides.properties";

    @Test
    void builderResourceLoadsOverridePropertiesFromClasspath() {
        final ClasspathOverrideSource source = ClasspathOverrideSource.builder()
                .resource(OVERRIDES_RESOURCE)
                .build();

        final Optional<OverrideContent> content = source.load();

        assertThat(content).isPresent();
        final List<Map.Entry<Predicate<Config.Key>, String>> overrides = content.orElseThrow().data().data();
        assertThat(overrides).hasSize(2);
        assertOverride(overrides, "app.message", "overridden from classpath");
        assertOverride(overrides, "app.services.primary.enabled", "true");
    }

    private static void assertOverride(List<Map.Entry<Predicate<Config.Key>, String>> overrides,
                                       String key,
                                       String value) {
        assertThat(overrides).anySatisfy(override -> {
            assertThat(override.getKey().test(Config.Key.create(key))).isTrue();
            assertThat(override.getValue()).isEqualTo(value);
        });
    }
}
