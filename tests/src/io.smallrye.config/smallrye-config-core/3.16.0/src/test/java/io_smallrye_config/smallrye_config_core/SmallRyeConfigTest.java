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
    void indexedPropertiesCanBeReadAsRequiredAndOptionalArrays() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "server.ports[1]", "8443",
                        "server.ports[0]", "8080",
                        "server.ports[2]", "9443"), "indexed-array-properties"))
                .build();

        Integer[] requiredPorts = config.getValue("server.ports", Integer[].class);
        Optional<Integer[]> optionalPorts = config.getOptionalValue("server.ports", Integer[].class);

        assertThat(requiredPorts).containsExactly(8080, 8443, 9443);
        assertThat(optionalPorts).hasValueSatisfying(ports -> assertThat(ports).containsExactly(8080, 8443, 9443));
    }
}
