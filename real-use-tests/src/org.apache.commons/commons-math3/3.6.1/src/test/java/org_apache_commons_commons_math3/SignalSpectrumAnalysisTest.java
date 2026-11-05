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

/// Signal processing identifies a repeating vibration and reconstructs its samples with the Fourier transform subsystem.
public class SignalSpectrumAnalysisTest {
    @Test
    public void identifiesTheDominantVibrationAndReconstructsSamples() {
        double[] vibrationSamples = {2.0, 3.0, 2.0, 1.0, 2.0, 3.0, 2.0, 1.0};
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

        Complex[] spectrum = transformer.transform(vibrationSamples, TransformType.FORWARD);
        Complex[] reconstructedSamples = transformer.transform(spectrum, TransformType.INVERSE);

        assertEquals(16.0, spectrum[0].getReal(), 1.0e-10);
        assertEquals(4.0, spectrum[2].abs(), 1.0e-10);
        assertEquals(4.0, spectrum[6].abs(), 1.0e-10);
        assertEquals(2.0, reconstructedSamples[0].getReal(), 1.0e-10);
        assertEquals(3.0, reconstructedSamples[1].getReal(), 1.0e-10);
        assertEquals(1.0, reconstructedSamples[3].getReal(), 1.0e-10);
    }
}
