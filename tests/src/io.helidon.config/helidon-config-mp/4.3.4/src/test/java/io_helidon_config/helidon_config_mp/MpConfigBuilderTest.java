/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config_mp;

import java.util.LinkedHashMap;
import java.util.Map;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class MpConfigBuilderTest {
    @Test
    void builtInClassConverterLoadsConfiguredClassName() {
        Config config = config(Map.of("target.class", MpConfigBuilderTest.class.getName()));

        Class<?> targetClass = config.getValue("target.class", Class.class);

        assertSame(MpConfigBuilderTest.class, targetClass);
    }

    private static Config config(Map<String, String> values) {
        return ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create("test-values", new LinkedHashMap<>(values)))
                .build();
    }
}
