/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.pulsar.shade.com.google.common.collect.ArrayTable;
import org.junit.jupiter.api.Test;

public class ArrayTableTest {
    @Test
    void exportsTypedTwoDimensionalArray() {
        ArrayTable<String, String, String> table = ArrayTable.create(
                List.of("row-one", "row-two"), List.of("left", "right"));
        table.put("row-one", "left", "a");
        table.put("row-one", "right", "b");
        table.put("row-two", "left", "c");
        table.put("row-two", "right", "d");

        String[][] values = table.toArray(String.class);

        assertThat(values).isDeepEqualTo(new String[][] {{"a", "b"}, {"c", "d"}});
        assertThat(values.getClass()).isEqualTo(String[][].class);
    }
}
