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
        FieldData data = FieldData.parse(PublicHolder.class.getField("value"));
        PublicHolder holder = new PublicHolder(42);

        assertThat(data.hostClass()).isEqualTo("PublicHolder");
        assertThat(data.name()).isEqualTo("value");
        assertThat(data.typeClass()).isEqualTo("int");
        assertThat(data.vmOffset()).isGreaterThanOrEqualTo(0);
        assertThat(data.safeValue(holder)).isEqualTo("42");
    }

    @Test
    void readsPrivateFieldValueAfterEnablingAccessibility() throws NoSuchFieldException {
        Field field = FieldDataPrivateHolder.class.getDeclaredField("value");
        FieldData data = FieldData.parse(field);
        FieldDataPrivateHolder holder = new FieldDataPrivateHolder(73);

        assertThat(data.hostClass()).isEqualTo("FieldDataPrivateHolder");
        assertThat(data.name()).isEqualTo("value");
        assertThat(data.typeClass()).isEqualTo("int");
        assertThat(data.vmOffset()).isGreaterThanOrEqualTo(0);
        assertThat(data.safeValue(holder)).isEqualTo("73");
    }

    public static class PublicHolder {
        public int value;

        PublicHolder(int value) {
            this.value = value;
        }
    }
}


class FieldDataPrivateHolder {
    private final int value;

    FieldDataPrivateHolder(int value) {
        this.value = value;
    }
}
