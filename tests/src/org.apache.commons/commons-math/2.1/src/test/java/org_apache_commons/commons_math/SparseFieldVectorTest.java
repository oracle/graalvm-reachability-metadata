/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math;

import org.apache.commons.math.fraction.Fraction;
import org.apache.commons.math.fraction.FractionField;
import org.apache.commons.math.linear.SparseFieldVector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SparseFieldVectorTest {
    @Test
    void toArrayAllocatesTypedArrayAndPreservesEntries() {
        Fraction[] values = new Fraction[] {
                Fraction.ONE,
                Fraction.ZERO,
                Fraction.TWO
        };
        SparseFieldVector<Fraction> vector = new SparseFieldVector<>(FractionField.getInstance(), values);

        Fraction[] entries = vector.toArray();

        assertThat(vector.getDimension()).isEqualTo(values.length);
        assertThat(entries).containsExactly(Fraction.ONE, Fraction.ZERO, Fraction.TWO);
    }
}
