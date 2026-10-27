/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.descriptor.java.LongPrimitiveArrayJavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LongPrimitiveArrayJavaTypeTest {

    @Test
    public void unwrapsLongArraysToRequestedArrayTypes() {
        Long[] boxed = LongPrimitiveArrayJavaType.INSTANCE.unwrap(
                new long[]{1L, 2L}, Long[].class, null
        );
        int[] emptyPrimitive = LongPrimitiveArrayJavaType.INSTANCE.unwrap(
                new long[0], int[].class, null
        );

        assertThat(boxed).containsExactly(1L, 2L);
        assertThat(emptyPrimitive).isEmpty();
    }
}
