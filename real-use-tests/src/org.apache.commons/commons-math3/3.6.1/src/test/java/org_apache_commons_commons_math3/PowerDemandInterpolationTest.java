/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Interpolation estimates power demand between the readings an application receives from a meter.
public class PowerDemandInterpolationTest {
    @Test
    public void estimatesDemandAtUnobservedTimes() {
        double[] recordedMinutes = {0.0, 15.0, 30.0, 45.0};
        double[] recordedKilowatts = {12.0, 18.0, 11.0, 17.0};
        PolynomialSplineFunction demandCurve = new LinearInterpolator().interpolate(
                recordedMinutes, recordedKilowatts);

        assertEquals(14.0, demandCurve.value(5.0), 1.0e-10);
        assertEquals(14.5, demandCurve.value(22.5), 1.0e-10);
        assertEquals(-7.0 / 15.0, demandCurve.derivative().value(22.5), 1.0e-10);
        assertEquals(17.0, demandCurve.value(45.0), 1.0e-10);
    }
}
