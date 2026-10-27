/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class JDK14UtilInnerRecordAccessorTest {
    @Test
    void mapsRecordComponentNamesTypesAndValues() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Coordinate coordinate = new Coordinate("north", 12);

        String json = mapper.writeValueAsString(coordinate);
        Coordinate restored = mapper.readValue("{\"label\":\"south\",\"distance\":8}", Coordinate.class);

        assertThat(json).contains("\"label\":\"north\"").contains("\"distance\":12");
        assertThat(restored).isEqualTo(new Coordinate("south", 8));
    }

    public record Coordinate(String label, int distance) {
    }
}
