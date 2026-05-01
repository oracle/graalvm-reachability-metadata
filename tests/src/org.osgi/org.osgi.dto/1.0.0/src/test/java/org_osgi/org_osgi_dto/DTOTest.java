/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_dto;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.osgi.dto.DTO;

import static org.assertj.core.api.Assertions.assertThat;

public class DTOTest {
    @Test
    void toStringReadsPublicFields() {
        SimpleDTO dto = new SimpleDTO();
        dto.name = "Framework";

        assertThat(dto.toString()).isEqualTo("{\"name\":\"Framework\"}");
    }

    @Test
    void toStringRendersNestedDTOsAndAggregates() {
        SimpleDTO child = new SimpleDTO();
        child.name = "Child";

        AggregateDTO dto = new AggregateDTO();
        dto.child = child;
        dto.values = Arrays.asList("one", 2, Boolean.TRUE);
        dto.tags = new String[] {"alpha", "beta"};
        dto.attributes = Collections.singletonMap("owner", "osgi");

        String rendered = dto.toString();

        assertThat(rendered)
                .contains("\"child\":{", "\"name\":\"Child\"}")
                .contains("\"values\":[\"one\",2,true]")
                .contains("\"tags\":[\"alpha\",\"beta\"]")
                .contains("\"attributes\":{\"owner\":\"osgi\"}")
                .doesNotContain("IGNORED_STATIC_FIELD");
    }

    public static class SimpleDTO extends DTO {
        public String name;
    }

    public static class AggregateDTO extends DTO {
        public static final String IGNORED_STATIC_FIELD = "ignored";

        public SimpleDTO child;
        public List<Object> values;
        public String[] tags;
        public Map<String, String> attributes;
    }
}
