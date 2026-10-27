/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.property.access.internal.PropertyAccessStrategyFieldImpl;
import org.hibernate.property.access.spi.Getter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GetterFieldImplTest {

    @Test
    public void readsEveryPrimitiveFieldKind() {
        PrimitiveValues values = new PrimitiveValues();

        assertThat(read(values, "booleanValue")).isEqualTo(true);
        assertThat(read(values, "byteValue")).isEqualTo((byte) 2);
        assertThat(read(values, "charValue")).isEqualTo('h');
        assertThat(read(values, "intValue")).isEqualTo(6);
        assertThat(read(values, "longValue")).isEqualTo(10L);
        assertThat(read(values, "shortValue")).isEqualTo((short) 1);
    }

    private static Object read(PrimitiveValues values, String propertyName) {
        Getter getter = PropertyAccessStrategyFieldImpl.INSTANCE
                .buildPropertyAccess(PrimitiveValues.class, propertyName, true)
                .getGetter();
        return getter.get(values);
    }

    public static class PrimitiveValues {
        private boolean booleanValue = true;
        private byte byteValue = 2;
        private char charValue = 'h';
        private int intValue = 6;
        private long longValue = 10L;
        private short shortValue = 1;
    }
}
