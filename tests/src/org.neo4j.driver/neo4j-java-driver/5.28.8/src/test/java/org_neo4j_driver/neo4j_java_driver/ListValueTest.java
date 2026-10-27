/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_neo4j_driver.neo4j_java_driver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

public class ListValueTest {
    @Test
    void mapsListValueToStringArray() {
        Value value = Values.value("node", "relationship", "path");

        String[] array = value.as(String[].class);

        assertThat(array).containsExactly("node", "relationship", "path");
    }
}
