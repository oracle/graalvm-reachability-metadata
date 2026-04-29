/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.FieldData;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldDataTest {
    @Test
    void readsPublicFieldValueDirectly() throws NoSuchFieldException {
        PublicFieldHolder holder = new PublicFieldHolder(7);
        FieldData fieldData = FieldData.parse(PublicFieldHolder.class.getField("value"));

        assertThat(fieldData.hostClass()).isEqualTo("PublicFieldHolder");
        assertThat(fieldData.name()).isEqualTo("value");
        assertThat(fieldData.typeClass()).isEqualTo("int");
        assertThat(fieldData.safeValue(holder)).isEqualTo("7");
    }

    @Test
    void readsPrivateFieldValueAfterMakingItAccessible() throws NoSuchFieldException {
        PrivateFieldHolder holder = new PrivateFieldHolder(99);
        FieldData fieldData = FieldData.parse(PrivateFieldHolder.class.getDeclaredField("value"));

        assertThat(fieldData.hostClass()).isEqualTo("PrivateFieldHolder");
        assertThat(fieldData.name()).isEqualTo("value");
        assertThat(fieldData.typeClass()).isEqualTo("int");
        assertThat(fieldData.safeValue(holder)).isEqualTo("99");
    }

    public static class PublicFieldHolder {
        public final int value;

        public PublicFieldHolder(int value) {
            this.value = value;
        }
    }

    private static class PrivateFieldHolder {
        private final int value;

        PrivateFieldHolder(int value) {
            this.value = value;
        }
    }
}
