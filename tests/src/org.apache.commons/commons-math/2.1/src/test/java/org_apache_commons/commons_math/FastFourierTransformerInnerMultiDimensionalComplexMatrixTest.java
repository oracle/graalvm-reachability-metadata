/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math;

import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.transform.FastFourierTransformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class FastFourierTransformerInnerMultiDimensionalComplexMatrixTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void multidimensionalTransformClonesComplexMatrixBeforeApplyingFft() {
        final Complex[][] matrix = new Complex[][] {
                {new Complex(1.0, 0.0), new Complex(2.0, 0.0)},
                {new Complex(3.0, 0.0), new Complex(4.0, 0.0)}
        };
        final FastFourierTransformer transformer = new FastFourierTransformer();

        final Complex[][] transformed = (Complex[][]) transformer.mdfft(matrix, true);

        assertThat(transformed).isNotSameAs(matrix);
        assertThat(transformed[0]).isNotSameAs(matrix[0]);
        assertComplex(transformed[0][0], 5.0, 0.0);
        assertComplex(transformed[0][1], -1.0, 0.0);
        assertComplex(transformed[1][0], -2.0, 0.0);
        assertComplex(transformed[1][1], 0.0, 0.0);
        assertComplex(matrix[0][0], 1.0, 0.0);
        assertComplex(matrix[0][1], 2.0, 0.0);
        assertComplex(matrix[1][0], 3.0, 0.0);
        assertComplex(matrix[1][1], 4.0, 0.0);
    }

    private static void assertComplex(Complex value, double real, double imaginary) {
        assertThat(value.getReal()).isCloseTo(real, within(TOLERANCE));
        assertThat(value.getImaginary()).isCloseTo(imaginary, within(TOLERANCE));
    }
}
