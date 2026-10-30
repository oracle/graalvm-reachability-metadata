/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config_mp;

import java.time.LocalDate;
import java.util.Map;

import io.helidon.config.mp.MpConfigSources;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class MpConfigBuilderTest {
    @Test
    void convertsConfiguredClassNameToClass() {
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(Map.of("date.type", "java.time.LocalDate")))
                .build();

        Class<?> dateType = config.getValue("date.type", Class.class);

        assertSame(LocalDate.class, dateType);
    }
}
