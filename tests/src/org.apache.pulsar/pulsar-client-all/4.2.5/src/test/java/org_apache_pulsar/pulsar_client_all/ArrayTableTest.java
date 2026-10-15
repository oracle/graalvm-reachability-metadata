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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ArrayTableTest {
    @Test
    void exportsTypedTwoDimensionalArray() {
        ArrayTable<String, String, String> table =
                ArrayTable.create(List.of("row-1", "row-2"), List.of("left", "right"));
        table.put("row-1", "left", "a");
        table.put("row-2", "right", "b");

        String[][] values = table.toArray(String.class);

        assertThat(values.length).isEqualTo(2);
        assertThat(values[0]).containsExactly("a", null);
        assertThat(values[1]).containsExactly(null, "b");
    }
}
