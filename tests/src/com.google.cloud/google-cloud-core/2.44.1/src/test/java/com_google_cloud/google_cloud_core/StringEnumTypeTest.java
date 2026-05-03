/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.cloud.StringEnumType;
import com.google.cloud.StringEnumValue;
import org.junit.jupiter.api.Test;

public class StringEnumTypeTest {
    @Test
    void returnsRegisteredValuesAsTypedArray() {
        StringEnumType<TestEnumValue> enumType =
                new StringEnumType<>(TestEnumValue.class, TestEnumValue::new);
        TestEnumValue first = enumType.createAndRegister("FIRST");
        TestEnumValue second = enumType.createAndRegister("SECOND");

        TestEnumValue[] values = enumType.values();

        assertThat(values).isInstanceOf(TestEnumValue[].class).containsExactly(first, second);
    }

    @Test
    void resolvesKnownAndUnknownConstants() {
        StringEnumType<TestEnumValue> enumType =
                new StringEnumType<>(TestEnumValue.class, TestEnumValue::new);
        TestEnumValue known = enumType.createAndRegister("KNOWN");

        assertThat(enumType.valueOf("KNOWN")).isSameAs(known);
        assertThat(enumType.valueOf("NEW")).isEqualTo(new TestEnumValue("NEW"));
        assertThatThrownBy(() -> enumType.valueOfStrict("MISSING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MISSING")
                .hasMessageContaining(TestEnumValue.class.getName());
    }

    public static final class TestEnumValue extends StringEnumValue {
        private static final long serialVersionUID = 1L;

        public TestEnumValue(String constant) {
            super(constant);
        }
    }
}
