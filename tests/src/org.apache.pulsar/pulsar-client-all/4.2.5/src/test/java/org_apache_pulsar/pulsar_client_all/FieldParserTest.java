/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.pulsar.common.util.FieldParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class FieldParserTest {
    @Test
    void updatesTypedFieldsAndInitializesEmptyCollections() {
        ParserConfig populated = new ParserConfig();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("threads", "8");
        values.put("enabled", "true");
        values.put("names", "alpha, beta");
        values.put("ports", "6650, 8080");
        values.put("labels", "zone=east, tier=storage");
        values.put("optionalName", "pulsar");
        FieldParser.update(values, populated);

        assertThat(populated.threads).isEqualTo(8);
        assertThat(populated.enabled).isTrue();
        assertThat(populated.names).containsExactly("alpha", "beta");
        assertThat(populated.ports).containsExactly(6650, 8080);
        assertThat(populated.labels)
                .containsEntry("zone", "east")
                .containsEntry("tier", "storage");
        assertThat(populated.optionalName).contains("pulsar");

        ParserConfig empty = new ParserConfig();
        FieldParser.update(
                Map.of(
                        "names", " ",
                        "ports", " ",
                        "optionalName", " ",
                        "nullableLimit", " "),
                empty);
        assertThat(empty.names).isEmpty();
        assertThat(empty.ports).isEmpty();
        assertThat(empty.optionalName).isEmpty();
        assertThat(empty.nullableLimit).isNull();
    }

    public static final class ParserConfig {
        private int threads;
        private boolean enabled;
        private List<String> names;
        private Set<Integer> ports;
        private Map<String, String> labels;
        private Optional<String> optionalName;
        private Integer nullableLimit = 1;
    }
}
