/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.ClickHouseValues;
import org.junit.jupiter.api.Test;

public class ClickHouseValuesTest {
    @Test
    void createsNestedObjectArrayWithConvertedElementType() {
        Object array = ClickHouseValues.createObjectArray(boolean.class, 2, 2);

        assertThat(array).isInstanceOf(Boolean[][].class);
        Boolean[][] values = (Boolean[][]) array;
        assertThat(values.length).isEqualTo(2);
        assertThat(values[0]).isEmpty();
        assertThat(values[1]).isEmpty();
    }

    @Test
    void createsNestedPrimitiveArrayWithConvertedElementType() {
        Object array = ClickHouseValues.createPrimitiveArray(Integer.class, 3, 2);

        assertThat(array).isInstanceOf(int[][].class);
        int[][] values = (int[][]) array;
        assertThat(values.length).isEqualTo(3);
        assertThat(values[0]).isEmpty();
        assertThat(values[1]).isEmpty();
        assertThat(values[2]).isEmpty();
    }
}
