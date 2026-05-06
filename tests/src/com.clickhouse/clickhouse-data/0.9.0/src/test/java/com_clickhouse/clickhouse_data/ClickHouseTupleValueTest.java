/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.value.ClickHouseTupleValue;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class ClickHouseTupleValueTest {
    @Test
    void createsTypedArrayFromTupleValues() {
        ClickHouseTupleValue value = ClickHouseTupleValue.of("alpha", "beta", "gamma");

        String[] strings = value.asArray(String.class);

        assertThat(strings).isInstanceOf(String[].class);
        assertThat(strings).containsExactly("alpha", "beta", "gamma");
    }
}
