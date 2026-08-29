/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.descriptor.java.IntegerPrimitiveArrayJavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntegerPrimitiveArrayJavaTypeTest {

    @Test
    public void unwrapsIntegerArraysToRequestedArrayTypes() {
        Integer[] boxed = IntegerPrimitiveArrayJavaType.INSTANCE.unwrap(
                new int[]{1, 2}, Integer[].class, null
        );
        long[] emptyPrimitive = IntegerPrimitiveArrayJavaType.INSTANCE.unwrap(
                new int[0], long[].class, null
        );

        assertThat(boxed).containsExactly(1, 2);
        assertThat(emptyPrimitive).isEmpty();
    }
}
