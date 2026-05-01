/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math;

import org.apache.commons.math.fraction.Fraction;
import org.apache.commons.math.fraction.FractionField;
import org.apache.commons.math.linear.ArrayFieldVector;
import org.apache.commons.math.linear.FieldVector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayFieldVectorTest {
    @Test
    void fieldSizedConstructorAllocatesTypedZeroFilledArray() {
        ArrayFieldVector<Fraction> vector = new ArrayFieldVector<>(FractionField.getInstance(), 3);

        Fraction[] entries = vector.toArray();

        assertThat(vector.getDimension()).isEqualTo(3);
        assertThat(entries).containsExactly(Fraction.ZERO, Fraction.ZERO, Fraction.ZERO);
    }

    @Test
    void appendScalarAllocatesTypedArrayWithOriginalAndAppendedEntries() {
        ArrayFieldVector<Fraction> vector = new ArrayFieldVector<>(new Fraction[] {
                Fraction.ONE,
                Fraction.TWO
        });

        FieldVector<Fraction> appended = vector.append(Fraction.ONE_HALF);
        Fraction[] entries = appended.toArray();

        assertThat(entries).containsExactly(Fraction.ONE, Fraction.TWO, Fraction.ONE_HALF);
        assertThat(vector.toArray()).containsExactly(Fraction.ONE, Fraction.TWO);
    }
}
