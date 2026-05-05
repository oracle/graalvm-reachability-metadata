/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.StringEnumType;
import org.junit.jupiter.api.Test;

public class StringEnumTypeTest {

    @Test
    void valuesReturnsTypedArrayContainingRegisteredConstantsInRegistrationOrder() {
        StringEnumType<StringEnumTypeTestValue> enumType = new StringEnumType<>(
            StringEnumTypeTestValue.class,
            StringEnumTypeTestValue::new);

        StringEnumTypeTestValue first = enumType.createAndRegister("FIRST");
        StringEnumTypeTestValue second = enumType.createAndRegister("SECOND");

        StringEnumTypeTestValue[] values = enumType.values();

        assertThat(values).containsExactly(first, second);
        assertThat(values.getClass().getComponentType()).isEqualTo(StringEnumTypeTestValue.class);
        assertThat(values).extracting(StringEnumTypeTestValue::constant).containsExactly("FIRST", "SECOND");
    }
}

final class StringEnumTypeTestValue {
    private final String constant;

    StringEnumTypeTestValue(String constant) {
        this.constant = constant;
    }

    String constant() {
        return constant;
    }
}
