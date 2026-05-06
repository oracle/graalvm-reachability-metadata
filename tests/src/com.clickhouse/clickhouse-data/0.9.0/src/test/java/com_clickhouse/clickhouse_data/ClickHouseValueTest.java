/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.ClickHouseValue;
import com.clickhouse.data.value.ClickHouseStringValue;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class ClickHouseValueTest {
    @Test
    void createsTypedArrayForScalarValue() {
        ClickHouseValue value = ClickHouseStringValue.of("alpha");

        String[] array = value.asArray(String.class);

        assertThat(array).isInstanceOf(String[].class);
        assertThat(array).containsExactly("alpha");
    }
}
