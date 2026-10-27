/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.descriptor.java.BooleanPrimitiveArrayJavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BooleanPrimitiveArrayJavaTypeTest {

    @Test
    public void unwrapsBooleanArraysToRequestedArrayTypes() {
        Boolean[] boxed = BooleanPrimitiveArrayJavaType.INSTANCE.unwrap(
                new boolean[]{true, false}, Boolean[].class, null
        );
        int[] emptyPrimitive = BooleanPrimitiveArrayJavaType.INSTANCE.unwrap(
                new boolean[0], int[].class, null
        );

        assertThat(boxed).containsExactly(true, false);
        assertThat(emptyPrimitive).isEmpty();
    }
}
