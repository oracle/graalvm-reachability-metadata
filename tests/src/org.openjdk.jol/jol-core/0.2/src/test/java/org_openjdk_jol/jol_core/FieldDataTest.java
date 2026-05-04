/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.ClassData;
import org.openjdk.jol.info.FieldData;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldDataTest {
    @Test
    void readsPublicFieldValueWithoutChangingAccessibility() {
        PublicFieldHolder holder = new PublicFieldHolder(42);
        FieldData fieldData = fieldData(PublicFieldHolder.class, "value");

        assertThat(fieldData.safeValue(holder)).isEqualTo("42");
    }

    @Test
    void readsPrivateFieldValueAfterMakingFieldAccessible() {
        PrivateFieldHolder holder = new PrivateFieldHolder(84);
        FieldData fieldData = fieldData(PrivateFieldHolder.class, "value");

        assertThat(fieldData.safeValue(holder)).isEqualTo("84");
    }

    private static FieldData fieldData(Class<?> holderClass, String fieldName) {
        ClassData classData = ClassData.parseClass(holderClass);
        for (FieldData fieldData : classData.fields()) {
            if (fieldData.name().equals(fieldName)) {
                return fieldData;
            }
        }
        throw new AssertionError("Field not found: " + fieldName);
    }

    public static final class PublicFieldHolder {
        public final int value;

        PublicFieldHolder(int value) {
            this.value = value;
        }
    }

    public static final class PrivateFieldHolder {
        private final int value;

        PrivateFieldHolder(int value) {
            this.value = value;
        }
    }
}
