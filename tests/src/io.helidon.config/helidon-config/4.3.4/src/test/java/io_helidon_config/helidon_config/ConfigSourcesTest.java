/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import io.helidon.config.ConfigSources;
import io.helidon.config.UrlConfigSource;
import io.helidon.config.spi.ConfigParser;

import org.junit.jupiter.api.Test;

public class ConfigSourcesTest {
    private static final String CLASSPATH_OVERRIDES_RESOURCE = "io_helidon_config/helidon_config/classpath-overrides.properties";

    @Test
    void classpathAllFindsMatchingResources() throws Exception {
        final List<UrlConfigSource.Builder> builders = ConfigSources.classpathAll(CLASSPATH_OVERRIDES_RESOURCE);

        assertThat(builders).isNotEmpty();
        final UrlConfigSource source = builders.get(0).build();
        assertThat(source.target().toString()).contains("classpath-overrides.properties");

        final Optional<ConfigParser.Content> content = source.load();
        assertThat(content).isPresent();
        try (InputStream data = content.orElseThrow().data()) {
            assertThat(new String(data.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("app.message=overridden from classpath");
        }
    }
}
