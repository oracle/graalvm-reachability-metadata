/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.value.ClickHouseArrayValue;
import com.clickhouse.data.value.ClickHouseIntegerValue;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class ClickHouseArrayValueTest {
    @Test
    void createsTypedEmptyArrayAndTypedCopy() {
        ClickHouseArrayValue<String> emptyValue = ClickHouseArrayValue.ofEmpty(String.class);
        assertThat(emptyValue.getValue()).isInstanceOf(String[].class).isEmpty();

        ClickHouseArrayValue<Integer> integerValue = ClickHouseArrayValue.of(new Integer[] {1, 2, 3 });
        Integer[] typedCopy = integerValue.asArray(Integer.class);

        assertThat(typedCopy).containsExactly(1, 2, 3);
        assertThat(typedCopy).isNotSameAs(integerValue.getValue());
    }

    @Test
    void updateUnknownWrapsArbitraryValueInConcreteTypedArray() {
        ClickHouseArrayValue<Object> value = ClickHouseArrayValue.ofEmpty(Object.class);
        StringBuilder payload = new StringBuilder("payload");

        value.update(payload);

        Object[] rawValue = value.getValue();
        assertThat(rawValue).isInstanceOf(StringBuilder[].class);
        assertThat(rawValue).containsExactly(payload);
    }

    @Test
    void setValuePromotesScalarToCompatibleNestedArrayDepth() {
        Number[][][] initialValue = new Number[][][] {new Number[][] {new Number[] {1 } } };
        ClickHouseArrayValue<Number[][]> value = ClickHouseArrayValue.of(initialValue);

        value.setValue(0, ClickHouseIntegerValue.of(42));

        Number[][][] rawValue = value.getValue();
        assertThat(rawValue[0]).isInstanceOf(Integer[][].class);
        assertThat(rawValue[0].length).isEqualTo(1);
        assertThat(rawValue[0][0]).isNull();
    }
}
