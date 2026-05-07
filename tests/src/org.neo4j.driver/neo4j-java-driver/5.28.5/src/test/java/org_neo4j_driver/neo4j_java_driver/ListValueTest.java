/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_neo4j_driver.neo4j_java_driver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

public class ListValueTest {
    @Test
    void mapsListValueToTypedArray() {
        Value value = Values.value(List.of("Neo4j", "GraalVM", "metadata"));

        String[] array = value.as(String[].class);

        assertThat(array).containsExactly("Neo4j", "GraalVM", "metadata");
    }
}
