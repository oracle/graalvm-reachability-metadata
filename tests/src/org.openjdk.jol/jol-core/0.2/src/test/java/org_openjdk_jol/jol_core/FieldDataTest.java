/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.FieldData;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldDataTest {

    @Test
    void readsPublicFieldValueWithoutChangingAccessibility() throws NoSuchFieldException {
        PublicFieldHolder holder = new PublicFieldHolder();
        Field field = PublicFieldHolder.class.getField("value");
        FieldData fieldData = FieldData.parse(field);

        assertThat(fieldData.hostClass()).isEqualTo("PublicFieldHolder");
        assertThat(fieldData.name()).isEqualTo("value");
        assertThat(fieldData.typeClass()).isEqualTo("int");
        assertThat(fieldData.safeValue(holder)).isEqualTo("42");
    }

    @Test
    void readsPrivateFieldValueAfterMakingFieldAccessible() throws NoSuchFieldException {
        PrivateFieldHolder holder = new PrivateFieldHolder();
        Field field = PrivateFieldHolder.class.getDeclaredField("value");
        FieldData fieldData = FieldData.parse(field);

        assertThat(fieldData.hostClass()).isEqualTo("PrivateFieldHolder");
        assertThat(fieldData.name()).isEqualTo("value");
        assertThat(fieldData.typeClass()).isEqualTo("int");
        assertThat(fieldData.safeValue(holder)).isEqualTo("84");
    }

    public static final class PublicFieldHolder {
        public int value = 42;
    }

    public static final class PrivateFieldHolder {
        private final int value = 84;
    }
}
