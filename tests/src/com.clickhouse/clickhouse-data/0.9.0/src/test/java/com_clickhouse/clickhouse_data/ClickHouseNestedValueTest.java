/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.ClickHouseColumn;
import com.clickhouse.data.value.ClickHouseNestedValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class ClickHouseNestedValueTest {
    @Test
    void createsTypedArrayOfNestedRows() {
        List<ClickHouseColumn> columns = ClickHouseColumn.parse("id Int32, label String");
        ClickHouseNestedValue value = ClickHouseNestedValue.of(columns, new Object[][] {
                {1, "alpha" },
                {2, "beta" }
        });

        Object[][] rows = value.asArray(Object[].class);

        assertThat(rows).isInstanceOf(Object[][].class);
        assertThat(rows).hasDimensions(2, 2);
        assertThat(rows[0]).containsExactly(1, "alpha");
        assertThat(rows[1]).containsExactly(2, "beta");
    }

    @Test
    void createsTypedMapOfColumnArrays() {
        List<ClickHouseColumn> columns = ClickHouseColumn.parse("id Int32, score Int32");
        ClickHouseNestedValue value = ClickHouseNestedValue.of(columns, new Object[][] {
                {1, 100 },
                {2, 200 },
                {3, 300 }
        });

        Map<String, Integer[]> columnValues = value.asMap(String.class, Integer[].class);

        assertThat(columnValues).containsOnlyKeys("id", "score");
        assertThat(columnValues.get("id")).isInstanceOf(Integer[].class).containsExactly(1, 2, 3);
        assertThat(columnValues.get("score")).isInstanceOf(Integer[].class).containsExactly(100, 200, 300);
    }
}
