/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_config.helidon_config;

import static org.assertj.core.api.Assertions.assertThat;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;

import org.junit.jupiter.api.Test;

public class ConfigMapperManagerTest {
    @Test
    void mapsListNodeToStringArrayThroughConfigMapper() {
        final ConfigNode.ListNode endpointList = ConfigNode.ListNode.builder()
                .addValue("alpha")
                .addValue("beta")
                .addValue("gamma")
                .build();
        final ConfigNode.ObjectNode objectNode = ConfigNode.ObjectNode.builder()
                .addList("app.endpoints", endpointList)
                .build();
        final Config config = Config.just(ConfigSources.create(objectNode));

        final String[] endpoints = config.get("app.endpoints").as(String[].class).get();

        assertThat(endpoints).containsExactly("alpha", "beta", "gamma");
    }
}
