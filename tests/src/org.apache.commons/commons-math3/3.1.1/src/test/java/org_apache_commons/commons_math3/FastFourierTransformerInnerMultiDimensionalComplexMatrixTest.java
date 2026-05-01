/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.junit.jupiter.api.Test;

public class FastFourierTransformerInnerMultiDimensionalComplexMatrixTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void multiDimensionalTransformClonesComplexMatrixStorage() {
        Complex[][] samples = {
            {complex(1.0), complex(2.0)},
            {complex(3.0), complex(4.0)}
        };
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

        Object transformed = transformer.mdfft(samples, TransformType.FORWARD);

        assertThat(transformed).isInstanceOf(Complex[][].class);
        assertThat(transformed).isNotSameAs(samples);
        Complex[][] matrix = (Complex[][]) transformed;
        assertThat(matrix[0]).isNotSameAs(samples[0]);
        assertThat(matrix[1]).isNotSameAs(samples[1]);
        assertComplex(matrix[0][0], 10.0, 0.0);
        assertComplex(matrix[0][1], -2.0, 0.0);
        assertComplex(matrix[1][0], -4.0, 0.0);
        assertComplex(matrix[1][1], 0.0, 0.0);
        assertComplex(samples[0][0], 1.0, 0.0);
        assertComplex(samples[1][1], 4.0, 0.0);
    }

    private static Complex complex(double real) {
        return new Complex(real, 0.0);
    }

    private static void assertComplex(Complex actual, double real, double imaginary) {
        assertThat(actual.getReal()).isCloseTo(real, offset(TOLERANCE));
        assertThat(actual.getImaginary()).isCloseTo(imaginary, offset(TOLERANCE));
    }
}
