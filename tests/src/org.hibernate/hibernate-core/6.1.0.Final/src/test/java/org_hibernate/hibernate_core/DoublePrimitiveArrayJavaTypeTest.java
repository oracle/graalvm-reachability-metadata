/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.descriptor.java.DoublePrimitiveArrayJavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DoublePrimitiveArrayJavaTypeTest {

    @Test
    public void unwrapsDoubleArraysToRequestedArrayTypes() {
        Double[] boxed = DoublePrimitiveArrayJavaType.INSTANCE.unwrap(
                new double[]{1.5, 2.5}, Double[].class, null
        );
        float[] emptyPrimitive = DoublePrimitiveArrayJavaType.INSTANCE.unwrap(
                new double[0], float[].class, null
        );

        assertThat(boxed).containsExactly(1.5, 2.5);
        assertThat(emptyPrimitive).isEmpty();
    }
}
