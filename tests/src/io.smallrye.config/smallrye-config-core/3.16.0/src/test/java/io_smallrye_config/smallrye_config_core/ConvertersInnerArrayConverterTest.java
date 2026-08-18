/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class ConvertersInnerArrayConverterTest {
    @Test
    void commaSeparatedPropertyCanBeReadAsObjectArray() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "server.ports", "8080,8443,9443"), "array-converter-properties"))
                .build();

        Integer[] ports = config.getValue("server.ports", Integer[].class);

        assertThat(ports).containsExactly(8080, 8443, 9443);
    }
}
