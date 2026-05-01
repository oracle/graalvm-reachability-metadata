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
import org.apache.commons.math3.linear.FieldVector;
import org.junit.jupiter.api.Test;

public class ArrayFieldVectorTest {
    @Test
    void fieldConstructorCreatesTypedZeroFilledStorage() {
        ArrayFieldVector<Fraction> vector = new ArrayFieldVector<Fraction>(FractionField.getInstance(), 3);

        assertThat(vector.getDimension()).isEqualTo(3);
        assertThat(vector.getData()).containsExactly(Fraction.ZERO, Fraction.ZERO, Fraction.ZERO);
    }

    @Test
    void operationsCreateTypedResultVectorsFromFieldRuntimeClass() {
        ArrayFieldVector<Fraction> vector = new ArrayFieldVector<Fraction>(FractionField.getInstance(), 3);
        vector.setEntry(0, new Fraction(1));
        vector.setEntry(1, new Fraction(2));
        vector.setEntry(2, new Fraction(3));

        FieldVector<Fraction> result = vector.mapAdd(new Fraction(1));

        assertThat(result).isInstanceOf(ArrayFieldVector.class);
        assertThat(result.toArray()).containsExactly(new Fraction(2), new Fraction(3), new Fraction(4));
    }
}
