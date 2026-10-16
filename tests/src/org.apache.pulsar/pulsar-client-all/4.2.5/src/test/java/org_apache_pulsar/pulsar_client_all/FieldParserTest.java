/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.pulsar.common.util.FieldParser;
import org.junit.jupiter.api.Test;

public class FieldParserTest {
    @Test
    void convertsUpdatesAndClearsConfigurationFields() {
        final Configuration configuration = new Configuration();
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "broker-a");
        values.put("port", "6650");
        values.put("clusters", "east, west");
        values.put("roles", "admin, proxy");
        values.put("weights", "primary=10, secondary=20");
        values.put("optionalPort", "7000");

        FieldParser.update(values, configuration);
        assertThat(configuration.name).isEqualTo("broker-a");
        assertThat(configuration.port).isEqualTo(6650);
        assertThat(configuration.clusters).containsExactly("east", "west");
        assertThat(configuration.roles).containsExactly("admin", "proxy");
        assertThat(configuration.weights).containsEntry("primary", 10).containsEntry("secondary", 20);
        assertThat(configuration.optionalPort).contains(7000);

        FieldParser.update(Map.of("clusters", "", "roles", " ", "optionalPort", "", "port", ""), configuration);
        assertThat(configuration.clusters).isEmpty();
        assertThat(configuration.roles).isEmpty();
        assertThat(configuration.optionalPort).isEmpty();
        assertThat(configuration.port).isNull();
    }

    public static final class Configuration {
        private String name = "initial";
        private Integer port = 1;
        private List<String> clusters = new ArrayList<>(List.of("initial"));
        private Set<String> roles = new LinkedHashSet<>(Set.of("initial"));
        private Map<String, Integer> weights = new LinkedHashMap<>(Map.of("initial", 1));
        private Optional<Integer> optionalPort = Optional.of(1);
    }
}
