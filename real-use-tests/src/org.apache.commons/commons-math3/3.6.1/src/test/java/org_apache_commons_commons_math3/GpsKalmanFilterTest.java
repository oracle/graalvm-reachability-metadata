/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.filter.DefaultMeasurementModel;
import org.apache.commons.math3.filter.DefaultProcessModel;
import org.apache.commons.math3.filter.KalmanFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Kalman filtering tracks a delivery vehicle from noisy GPS position readings.
public class GpsKalmanFilterTest {
    @Test
    public void estimatesVehiclePositionAndSpeedFromGpsReadings() {
        DefaultProcessModel vehicleMotion = new DefaultProcessModel(
                new double[][] {{1.0, 1.0}, {0.0, 1.0}},
                new double[][] {{0.0}, {0.0}},
                new double[][] {{0.01, 0.0}, {0.0, 0.01}},
                new double[] {0.0, 10.0},
                new double[][] {{100.0, 0.0}, {0.0, 100.0}});
        DefaultMeasurementModel gps = new DefaultMeasurementModel(
                new double[][] {{1.0, 0.0}}, new double[][] {{25.0}});
        KalmanFilter tracker = new KalmanFilter(vehicleMotion, gps);

        double[] gpsPositions = {12.0, 19.0, 31.0, 39.0};
        for (double gpsPosition : gpsPositions) {
            tracker.predict();
            tracker.correct(new double[] {gpsPosition});
        }

        assertEquals(2, tracker.getStateDimension());
        assertEquals(1, tracker.getMeasurementDimension());
        assertEquals(39.4134862184, tracker.getStateEstimation()[0], 1.0e-9);
        assertEquals(9.5010098886, tracker.getStateEstimation()[1], 1.0e-9);
        assertEquals(15.9780523548, tracker.getErrorCovarianceMatrix().getEntry(0, 0), 1.0e-9);
    }
}
