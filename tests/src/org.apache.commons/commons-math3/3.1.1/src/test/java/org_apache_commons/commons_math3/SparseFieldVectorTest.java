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

@SuppressWarnings("deprecation")
public class SparseFieldVectorTest {
    @Test
    void toArrayCreatesRuntimeTypedArrayFromFieldElements() {
        SparseFieldVector<Fraction> vector = new SparseFieldVector<Fraction>(FractionField.getInstance(), 4);
        vector.setEntry(0, new Fraction(1, 2));
        vector.setEntry(1, new Fraction(2, 3));
        vector.setEntry(2, Fraction.ZERO);
        vector.setEntry(3, new Fraction(4, 5));

        Fraction[] values = vector.toArray();

        assertThat(values).isInstanceOf(Fraction[].class);
        assertThat(values).containsExactly(new Fraction(1, 2), new Fraction(2, 3), Fraction.ZERO, new Fraction(4, 5));
    }
}
