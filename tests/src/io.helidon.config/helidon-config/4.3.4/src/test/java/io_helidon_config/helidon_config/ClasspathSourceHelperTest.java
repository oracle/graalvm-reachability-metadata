/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config;

import static org.assertj.core.api.Assertions.assertThat;

import io.helidon.config.ClasspathOverrideSource;

import org.junit.jupiter.api.Test;

public class ClasspathSourceHelperTest {
    private static final String OVERRIDES_RESOURCE = "io_helidon_config/helidon_config/classpath-overrides.properties";

    @Test
    void classpathOverrideDescriptionIdentifiesClasspathResource() {
        final ClasspathOverrideSource source = ClasspathOverrideSource.builder()
                .resource(OVERRIDES_RESOURCE)
                .build();

        final String description = source.description();

        assertThat(description)
                .startsWith("ClasspathOverride[")
                .contains("classpath-overrides.properties")
                .endsWith("]");
    }
}
