/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.hadoop.thirdparty.com.google.common.collect.ArrayTable;
import org.junit.jupiter.api.Test;

public class ArrayTableTest {
    @Test
    void toArrayReturnsTypedSnapshotUsingTableOrdering() {
        ArrayTable<String, String, String> table = ArrayTable.create(
                List.of("row-one", "row-two"),
                List.of("first", "second", "third"));
        table.put("row-one", "first", "one-first");
        table.put("row-one", "second", "one-second");
        table.put("row-two", "first", "two-first");
        table.put("row-two", "third", "two-third");

        String[][] snapshot = table.toArray(String.class);

        assertThat(snapshot).hasDimensions(2, 3);
        assertThat(snapshot[0]).containsExactly("one-first", "one-second", null);
        assertThat(snapshot[1]).containsExactly("two-first", null, "two-third");

        table.set(0, 0, "updated");
        assertThat(snapshot[0][0]).isEqualTo("one-first");
    }
}
