/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.descriptor.java.FloatPrimitiveArrayJavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FloatPrimitiveArrayJavaTypeTest {

    @Test
    public void unwrapsFloatArraysToRequestedArrayTypes() {
        Float[] boxed = FloatPrimitiveArrayJavaType.INSTANCE.unwrap(
                new float[]{1.5f, 2.5f}, Float[].class, null
        );
        double[] emptyPrimitive = FloatPrimitiveArrayJavaType.INSTANCE.unwrap(
                new float[0], double[].class, null
        );

        assertThat(boxed).containsExactly(1.5f, 2.5f);
        assertThat(emptyPrimitive).isEmpty();
    }
}
