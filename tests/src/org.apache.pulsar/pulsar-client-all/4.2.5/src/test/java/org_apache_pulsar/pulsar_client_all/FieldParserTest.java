/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.pulsar.common.util.FieldParser;
import org.junit.jupiter.api.Test;

public class FieldParserTest {
    @Test
    void convertsAndAssignsScalarAndContainerConfiguration() {
        Configuration target = new Configuration();
        Map<String, String> values = Map.of(
                "port", "6650",
                "enabled", "true",
                "names", "one, two",
                "roles", "admin, proxy, admin",
                "optionalPort", "6651");

        FieldParser.update(values, target);

        assertThat(target.port).isEqualTo(6650);
        assertThat(target.enabled).isTrue();
        assertThat(target.names).containsExactly("one", "two");
        assertThat(target.roles).containsExactly("admin", "proxy");
        assertThat(target.optionalPort).contains(6651);
        assertThat(FieldParser.convert("17", Long.class)).isEqualTo(17L);
    }

    @Test
    void assignsEmptyValuesToSupportedReferenceFields() {
        Configuration target = new Configuration();

        FieldParser.update(Map.of("port", "", "names", " ", "roles", "", "optionalPort", " "), target);

        assertThat(target.port).isNull();
        assertThat(target.names).isEmpty();
        assertThat(target.roles).isEmpty();
        assertThat(target.optionalPort).isEmpty();
    }

    public static class Configuration {
        private Integer port = 1;
        private Boolean enabled = false;
        private List<String> names = List.of("initial");
        private Set<String> roles = Set.of("initial");
        private Optional<Integer> optionalPort = Optional.of(1);
    }
}
