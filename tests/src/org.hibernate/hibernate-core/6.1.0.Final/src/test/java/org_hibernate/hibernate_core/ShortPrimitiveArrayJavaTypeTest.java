/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.descriptor.java.ShortPrimitiveArrayJavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ShortPrimitiveArrayJavaTypeTest {

    @Test
    public void unwrapsShortArraysToRequestedArrayTypes() {
        Short[] boxed = ShortPrimitiveArrayJavaType.INSTANCE.unwrap(
                new short[]{1, 2}, Short[].class, null
        );
        long[] emptyPrimitive = ShortPrimitiveArrayJavaType.INSTANCE.unwrap(
                new short[0], long[].class, null
        );

        assertThat(boxed).containsExactly((short) 1, (short) 2);
        assertThat(emptyPrimitive).isEmpty();
    }
}
