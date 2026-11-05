/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Linear algebra solves a sensor calibration model before an application uses it.
public class SensorCalibrationLinearAlgebraTest {
    @Test
    public void calibratesSensorReadingsAndPredictsAnUnseenReading() {
        double[][] calibrationReadings = {
                {1.0, 0.0, 0.0},
                {1.0, 1.0, 1.0},
                {1.0, 2.0, 4.0},
                {1.0, 3.0, 9.0},
                {1.0, 4.0, 16.0}
        };
        double[] measuredTemperatures = {6.0, 7.5, 8.0, 7.5, 6.0};

        DecompositionSolver solver = new QRDecomposition(
                MatrixUtils.createRealMatrix(calibrationReadings)).getSolver();
        RealVector calibration = solver.solve(MatrixUtils.createRealVector(measuredTemperatures));

        assertEquals(6.0, calibration.getEntry(0), 1.0e-10);
        assertEquals(2.0, calibration.getEntry(1), 1.0e-10);
        assertEquals(-0.5, calibration.getEntry(2), 1.0e-10);

        RealVector unseenReading = MatrixUtils.createRealVector(new double[] {1.0, 5.0, 25.0});
        assertEquals(3.5, unseenReading.dotProduct(calibration), 1.0e-10);
    }
}
