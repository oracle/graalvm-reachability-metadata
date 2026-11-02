/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.example.geographiclib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.Pair;
import net.sf.geographiclib.PolygonArea;
import net.sf.geographiclib.PolygonResult;
import org.junit.jupiter.api.Test;

/** The polygon-area subsystem measures a geodesic geofence around application locations. */
public class GeodesicFenceAreaTest {
    private static final double METER_TOLERANCE = 1.0e-6;

    @Test
    void measuresPerimeterAndAreaOfLandmarkGeofence() {
        PolygonArea geofence = new PolygonArea(Geodesic.WGS84, false);
        geofence.AddPoint(40.7484, -73.9857);
        geofence.AddPoint(40.6892, -74.0445);
        geofence.AddPoint(40.7003, -73.9967);
        geofence.AddPoint(40.7580, -73.9855);

        Pair currentLocation = geofence.CurrentPoint();
        PolygonResult measurement = geofence.Compute();

        assertThat(currentLocation.first).isCloseTo(40.7580, within(METER_TOLERANCE));
        assertThat(currentLocation.second).isCloseTo(-73.9855, within(METER_TOLERANCE));
        assertThat(measurement.num).isEqualTo(4);
        assertThat(measurement.perimeter).isCloseTo(20007.383747575863, within(METER_TOLERANCE));
        assertThat(measurement.area).isCloseTo(9768651.726931274, within(METER_TOLERANCE));
    }
}
