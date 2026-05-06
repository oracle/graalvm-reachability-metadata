/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.ClickHouseArraySequence;
import com.clickhouse.data.ClickHouseColumn;
import com.clickhouse.data.ClickHouseDataConfig;
import com.clickhouse.data.ClickHouseDataType;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class ClickHouseColumnTest {
    @Test
    void createsPrimitiveMultiDimensionalArrayValueForNestedArrayColumn() {
        ClickHouseColumn column = ClickHouseColumn.of("measurements", "Array(Array(Int32))");
        ClickHouseDataConfig config = new ClickHouseDataConfig() {
            @Override
            public TimeZone getTimeZoneForDate() {
                return TimeZone.getDefault();
            }

            @Override
            public TimeZone getUseTimeZone() {
                return TimeZone.getDefault();
            }
        };

        ClickHouseArraySequence value = column.newArrayValue(config);

        Object[] array = value.asArray();
        assertThat(column.getArrayNestedLevel()).isEqualTo(2);
        assertThat(column.getArrayBaseColumn().getDataType()).isEqualTo(ClickHouseDataType.Int32);
        assertThat(array).isInstanceOf(int[][].class).isEmpty();
    }
}
