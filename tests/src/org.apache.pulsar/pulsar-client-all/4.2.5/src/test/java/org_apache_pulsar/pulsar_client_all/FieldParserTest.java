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
    void updatesScalarsAndGenericContainersFromConfigurationText() {
        final ParserTarget target = new ParserTarget();
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "primary");
        values.put("port", "6650");
        values.put("enabled", "true");
        values.put("clusters", "east, west");
        values.put("roles", "reader, writer, reader");
        values.put("weights", "east=4, west=6");
        values.put("fallbackPort", "6651");

        FieldParser.update(values, target);

        assertThat(target.name).isEqualTo("primary");
        assertThat(target.port).isEqualTo(6650);
        assertThat(target.enabled).isTrue();
        assertThat(target.clusters).containsExactly("east", "west");
        assertThat(target.roles).containsExactly("reader", "writer");
        assertThat(target.weights).containsEntry("east", 4).containsEntry("west", 6);
        assertThat(target.fallbackPort).contains(6651);
    }

    @Test
    void clearsOptionalCollectionAndNumberValuesForBlankConfiguration() {
        final ParserTarget target = new ParserTarget();

        FieldParser.update(Map.of("port", "", "clusters", " ", "fallbackPort", ""), target);

        assertThat(target.port).isNull();
        assertThat(target.clusters).isEmpty();
        assertThat(target.fallbackPort).isEmpty();
    }

    public static final class ParserTarget {
        private String name = "unset";
        private Integer port = 1;
        private Boolean enabled = false;
        private List<String> clusters = new ArrayList<>(List.of("initial"));
        private Set<String> roles = new LinkedHashSet<>();
        private Map<String, Integer> weights = new LinkedHashMap<>();
        private Optional<Integer> fallbackPort = Optional.of(1);
    }
}
