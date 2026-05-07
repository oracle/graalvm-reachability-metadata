/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.pulsar.common.util.FieldParser;
import org.junit.jupiter.api.Test;

public class FieldParserTest {
    @Test
    void updateConvertsConfiguredValuesAndClearsBlankGenericAndNumberValues() {
        ParserTarget target = new ParserTarget();
        Map<String, String> properties = Map.of(
                "name", "updated",
                "enabled", "true",
                "ports", "",
                "names", " ",
                "mode", "\t",
                "limit", "");

        FieldParser.update(properties, target);

        assertThat(target.getName()).isEqualTo("updated");
        assertThat(target.isEnabled()).isTrue();
        assertThat(target.getPorts()).isEmpty();
        assertThat(target.getNames()).isEmpty();
        assertThat(target.getMode()).isEmpty();
        assertThat(target.getLimit()).isNull();
    }

    private static final class ParserTarget {
        private String name = "initial";
        private boolean enabled;
        private List<Integer> ports = new ArrayList<>(List.of(6650));
        private Set<String> names = new LinkedHashSet<>(Set.of("pulsar"));
        private Optional<String> mode = Optional.of("persistent");
        private Integer limit = 10;

        String getName() {
            return name;
        }

        boolean isEnabled() {
            return enabled;
        }

        List<Integer> getPorts() {
            return ports;
        }

        Set<String> getNames() {
            return names;
        }

        Optional<String> getMode() {
            return mode;
        }

        Integer getLimit() {
            return limit;
        }
    }
}
