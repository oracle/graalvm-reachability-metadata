/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Fourier transforms identify vibration frequencies in a sampled machine signal.
public class VibrationSpectrumTransformTest {
    @Test
    public void identifiesHarmonicsAndReconstructsTheSignal() {
        double[] vibrationSamples = new double[8];
        for (int sample = 0; sample < vibrationSamples.length; sample++) {
            vibrationSamples[sample] = 10.0
                    + 3.0 * Math.cos(2.0 * Math.PI * sample / vibrationSamples.length)
                    + 2.0 * Math.cos(4.0 * Math.PI * sample / vibrationSamples.length);
        }

        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] spectrum = transformer.transform(vibrationSamples, TransformType.FORWARD);
        Complex[] reconstructed = transformer.transform(spectrum, TransformType.INVERSE);

        assertEquals(80.0, spectrum[0].getReal(), 1.0e-10);
        assertEquals(12.0, spectrum[1].getReal(), 1.0e-10);
        assertEquals(8.0, spectrum[2].getReal(), 1.0e-10);
        assertEquals(12.0, spectrum[7].getReal(), 1.0e-10);
        assertEquals(0.0, spectrum[1].getImaginary(), 1.0e-10);
        assertEquals(0.0, spectrum[2].getImaginary(), 1.0e-10);
        for (int sample = 0; sample < vibrationSamples.length; sample++) {
            assertEquals(vibrationSamples[sample], reconstructed[sample].getReal(), 1.0e-10);
            assertEquals(0.0, reconstructed[sample].getImaginary(), 1.0e-10);
        }
    }
}
