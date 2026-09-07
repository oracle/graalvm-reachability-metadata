/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ArrayTable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayTableTest {
    @Test
    void exportsValuesToATypedTwoDimensionalArray() {
        ArrayTable<String, String, String> table = ArrayTable.create(List.of("row"), List.of("first", "second"));
        table.put("row", "first", "one");
        table.put("row", "second", "two");

        assertThat(table.toArray(String.class)[0]).containsExactly("one", "two");
    }
}
