/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.math3.fraction.Fraction;
import org.apache.commons.math3.fraction.FractionField;
import org.apache.commons.math3.linear.ArrayFieldVector;
import org.junit.jupiter.api.Test;

public class ArrayFieldVectorTest {
    @Test
    void constructorAllocatesTypedStorageFromFieldRuntimeClass() {
        ArrayFieldVector<Fraction> vector = new ArrayFieldVector<>(FractionField.getInstance(), 3);

        assertThat(vector.getDimension()).isEqualTo(3);
        assertThat(vector.toArray()).isInstanceOf(Fraction[].class);
        assertThat(vector.toArray()).containsExactly(Fraction.ZERO, Fraction.ZERO, Fraction.ZERO);
    }
}
