/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.MapConfigSource;

import org.junit.jupiter.api.Test;

public class ConfigMappersTest {
    @Test
    void mapsClassValuesThroughBuiltInMapper() {
        final Config config = Config.just(MapConfigSource.create(Map.of("app.target-class", "java.lang.String")));

        final Class<?> targetClass = config.get("app.target-class").as(Class.class).get();

        assertThat(targetClass).isEqualTo(String.class);
    }
}
