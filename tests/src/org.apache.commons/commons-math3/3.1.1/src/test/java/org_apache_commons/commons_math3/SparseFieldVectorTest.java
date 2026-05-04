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
import org.apache.commons.math3.linear.SparseFieldVector;
import org.junit.jupiter.api.Test;

public class SparseFieldVectorTest {
    @Test
    void toArrayAllocatesTypedStorageFromFieldRuntimeClass() {
        SparseFieldVector<Fraction> vector = new SparseFieldVector<>(FractionField.getInstance(), new Fraction[] {
                Fraction.ONE,
                Fraction.ZERO,
                new Fraction(2, 3),
                new Fraction(-5, 7)
        });

        Fraction[] values = vector.toArray();

        assertThat(values).isInstanceOf(Fraction[].class);
        assertThat(values).containsExactly(Fraction.ONE, Fraction.ZERO, new Fraction(2, 3), new Fraction(-5, 7));
    }
}
