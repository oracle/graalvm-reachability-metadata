/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Curve fitting estimates a quadratic temperature trend from calibration samples.
public class TemperatureTrendCurveFittingTest {
    @Test
    public void estimatesTrendAndPredictsNextTemperature() {
        WeightedObservedPoints observations = new WeightedObservedPoints();
        observations.add(0.0, 18.0);
        observations.add(1.0, 20.5);
        observations.add(2.0, 22.0);
        observations.add(3.0, 22.5);
        observations.add(4.0, 22.0);

        double[] coefficients = PolynomialCurveFitter.create(2).fit(observations.toList());

        assertEquals(18.0, coefficients[0], 1.0e-10);
        assertEquals(3.0, coefficients[1], 1.0e-10);
        assertEquals(-0.5, coefficients[2], 1.0e-10);
        assertEquals(22.5, evaluate(coefficients, 3.0), 1.0e-10);
    }

    private double evaluate(double[] coefficients, double time) {
        return coefficients[0] + coefficients[1] * time + coefficients[2] * time * time;
    }
}
